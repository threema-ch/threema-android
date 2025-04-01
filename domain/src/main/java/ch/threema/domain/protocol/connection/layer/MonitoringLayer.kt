/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.connection.PayloadProcessingException
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.ProcessingPipe
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.data.CspContainer
import ch.threema.domain.protocol.connection.data.D2mProtocolException
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundL3Message
import ch.threema.domain.protocol.connection.data.InboundL4Message
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundL4Message
import ch.threema.domain.protocol.connection.data.OutboundL5Message
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.Layer4Controller
import ch.threema.domain.protocol.connection.util.MdLayer4Controller
import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

private val logger = ConnectionLoggingUtil.getConnectionLogger("MonitoringLayer")

internal class MonitoringLayer(
    private val connection: ServerConnection,
    private val controller: Layer4Controller
) : Layer4Codec {
    private companion object {
        // Preserve echo sequence number across new instance creations
        private var lastSentEchoSeq: Int = 0
        private var lastRcvdEchoSeq: Int = 0

        // The count of "another connection" error messages
        // is reset, when a new connection session is started
        private var anotherConnectionCount: Int = 0
    }

    private val mdController: MdLayer4Controller by lazy {
        if (controller is MdLayer4Controller) {
            controller
        } else {
            throw ServerConnectionException("Requested md controller in non-md configuration")
        }
    }

    private var echoRequestJob: Job? = null

    private val inbound = ProcessingPipe<InboundL3Message, InboundL4Message>(this::handleInbound)
    private val outbound =
        ProcessingPipe<OutboundL5Message, OutboundL4Message>(this::handleOutbound)

    override val encoder: PipeProcessor<OutboundL5Message, OutboundL4Message> = outbound
    override val decoder: PipeProcessor<InboundL3Message, InboundL4Message> = inbound

    private var stopped = false

    init {
        CoroutineScope(controller.dispatcher.coroutineContext).launch {
            launch {
                controller.cspAuthenticated.await()
                startMonitoring()
            }
            launch {
                controller.connectionClosed.await()
                stopMonitoring()
            }
        }
        if (connection.isNewConnectionSession) {
            logger.debug("Reset another connection count")
            anotherConnectionCount = 0
        }
    }

    private fun handleInbound(message: InboundL3Message) {
        controller.dispatcher.assertDispatcherContext()

        logger.trace("Handle inbound message of type `{}`", message.type)
        when (message) {
            is CspContainer -> handleInboundCspContainer(message)
            is InboundD2mMessage -> handleInboundD2mMessage(message)
        }
    }

    private fun handleOutbound(message: OutboundL5Message) {
        logger.trace("Handle outbound message of type `{}`", message.type)
        outbound.send(mapOutbound(message))
    }

    private fun mapOutbound(message: OutboundL5Message): OutboundL4Message {
        controller.dispatcher.assertDispatcherContext()

        return when (message) {
            is CspContainer -> message
            is OutboundD2mMessage -> message
        }
    }

    private fun handleInboundD2mMessage(message: InboundD2mMessage) {
        when (message) {
            is InboundD2mMessage.RolePromotedToLeader -> handlePromotedToLeader()
            is InboundD2mMessage.ReflectionQueueDry -> mdController.reflectionQueueDry.complete(Unit)
            else -> inbound.send(message)
        }
    }

    private fun handlePromotedToLeader() {
        if (!mdController.reflectionQueueDry.isCompleted || mdController.reflectionQueueDry.isCancelled) {
            throw D2mProtocolException("RolePromotedToLeader was received before ReflectionQueueDry")
        }
        CoroutineScope(controller.dispatcher.coroutineContext).launch {
            controller.cspAuthenticated.await()
            logger.debug("Send UnblockIncomingMessage to chat server")
            // We can send unblock incoming messages directly as we will process the messages
            // sequentially, i.e., the reflected messages will be processed before new messages
            outbound.send(
                CspContainer(
                    ProtocolDefines.PLTYPE_UNBLOCK_INCOMING_MESSAGES.toUByte(),
                    ByteArray(0)
                )
            )
        }
    }

    private fun handleInboundCspContainer(message: CspContainer) {
        when (message.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_ECHO_REPLY -> handleEchoReply(message)
            ProtocolDefines.PLTYPE_ERROR -> handleCloseError(message)
            else -> inbound.send(message)
        }
    }

    /**
     * This only handles the `can-reconnect` flag of the close error.
     * Handling of the message (e.g. Display it to the user) will be handled
     * by the task manager
     */
    private fun handleCloseError(container: CspContainer) {
        val closeError = container.toInboundMessage().toServerErrorData()

        /* close-errors can be ignored to the extend that the message has not to be
         * shown to the user. If there actually is another device connecting to the
         * server this will be signaled by the device cookie change indication (a
         * corresponding message will be displayed to the user).
         *
         * In order for the device cookie change indication to show up a reconnect
         * has to be performed. Therefore we ignore the `canReconnect` flag for up
         * to five times.
         *
         * There might be some strange situations where the `another connection` might occur
         * on a single device due to weird timing when switching between networks. In this
         * case it is still the same device and therefore no problem.
         * If the second connection actually stems from another device, a device cookie change
         * indication will be triggered upon the next connection to the chat server.
         *
         * Note: If there are two connections to the chat server from the same id, only the
         * first connection will be closed and receive a server error. The Second connection
         * will be left untouched and not be notified.
         *
         * TODO(ANDR-2474): Consolidate "another connection" behaviour
         */
        if (closeError.message.contains("Another connection") && anotherConnectionCount < 3) {
            // Ignore `canReconnect` flag
            anotherConnectionCount++
            logger.warn("Ignore `another connection` error #{}", anotherConnectionCount)
        } else {
            if (!closeError.canReconnect) {
                connection.disableReconnect()
            }
            inbound.send(container)
        }
    }

    private fun handleEchoReply(message: CspContainer) {
        logger.debug("Handle echo reply")
        controller.dispatcher.assertDispatcherContext()

        val data = message.data
        if (data.size != 12) {
            throw PayloadProcessingException("Bad length (${data.size}) for echo reply payload")
        }
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
        lastRcvdEchoSeq = buffer.int
        val rttMs = Date().time - buffer.long
        logger.info("Received echo reply (seq: {}, rtt: {} ms) ", lastRcvdEchoSeq, rttMs)
    }

    private fun startMonitoring() {
        controller.dispatcher.assertDispatcherContext()

        val (echoRequestInterval, echoResponseTimeout, connectionIdleTimeout) = if (controller is MdLayer4Controller) {
            // Multi device is active
            Triple(
                ProtocolDefines.ECHO_REQUEST_INTERVAL_MD,
                ProtocolDefines.ECHO_RESPONSE_TIMEOUT,
                ProtocolDefines.CONNECTION_IDLE_TIMEOUT_MD
            )
        } else {
            Triple(
                ProtocolDefines.ECHO_REQUEST_INTERVAL_CSP,
                ProtocolDefines.ECHO_RESPONSE_TIMEOUT,
                ProtocolDefines.CONNECTION_IDLE_TIMEOUT_CSP
            )
        }
        logger.debug(
            "echoRequestInterval={}, echoResponseTimeout={}, connectionIdleTimeout={}",
            echoRequestInterval,
            echoResponseTimeout,
            connectionIdleTimeout
        )

        if (stopped) {
            logger.warn("Ignore attempt to start monitoring after monitoring has already been stopped")
        } else {
            logger.trace("Set connection idle timeout to {} seconds", connectionIdleTimeout)
            outbound.send(prepareSetConnectionIdleTimeout(connectionIdleTimeout))
            logger.debug("Start periodic echo requests")
            echoRequestJob = CoroutineScope(controller.dispatcher.coroutineContext).launch {
                while (true) {
                    delay(echoRequestInterval * 1000L)
                    val sequence = sendEchoRequest()
                    launch {
                        expectEchoResponse(sequence, echoResponseTimeout)
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        controller.dispatcher.assertDispatcherContext()
        logger.debug("Stop periodic echo requests")

        stopped = true

        echoRequestJob?.cancel()
        echoRequestJob = null
    }

    /**
     * @return the sequence number of the sent echo request
     */
    private fun sendEchoRequest(): Int {
        controller.dispatcher.assertDispatcherContext()

        val sequence = ++lastSentEchoSeq
        logger.info("Sending echo request (seq: {})", sequence)
        outbound.send(prepareEchoRequest(sequence))
        return sequence
    }

    private suspend fun expectEchoResponse(expectedSequence: Int, responseTimeoutS: Short) {
        delay(responseTimeoutS * 1000L)
        if (lastRcvdEchoSeq < expectedSequence) {
            logger.info(
                "No reply to echo request (seq: {}); terminate connection",
                expectedSequence
            )
            controller.ioProcessingStoppedSignal.completeExceptionally(ServerConnectionException("No reply to echo request"))
        }
    }

    private fun prepareEchoRequest(sequenceNumber: Int): CspContainer {
        val echoData = ByteBuffer.wrap(ByteArray(12))
            .order(ByteOrder.nativeOrder())
            .putInt(sequenceNumber)
            .putLong(Date().time)
            .array()
        return CspContainer(ProtocolDefines.PLTYPE_ECHO_REQUEST.toUByte(), echoData)
    }

    private fun prepareSetConnectionIdleTimeout(idleTimeout: Short): CspContainer {
        if (idleTimeout < 30 || idleTimeout > 600) {
            throw ServerConnectionException("Invalid connection idle timeout: $idleTimeout")
        }
        val timeoutBytes = ByteBuffer
            .allocate(Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(idleTimeout)
            .array()
        return CspContainer(
            ProtocolDefines.PLTYPE_SET_CONNECTION_IDLE_TIMEOUT.toUByte(),
            timeoutBytes
        )
    }
}
