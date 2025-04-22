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

import ch.threema.domain.protocol.connection.ConnectionLock
import ch.threema.domain.protocol.connection.ConnectionLockProvider
import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.Pipe
import ch.threema.domain.protocol.connection.PipeCloseHandler
import ch.threema.domain.protocol.connection.PipeHandler
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.csp.CspConnection
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundL4Message
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundL5Message
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private val logger = ConnectionLoggingUtil.getConnectionLogger("EndToEndLayer")

internal class EndToEndLayer(
    private val outputDispatcher: CoroutineContext,
    private val connectionController: ServerConnectionController,
    private val connection: ServerConnection,
    private val incomingMessageProcessor: IncomingMessageProcessor,
    private val taskManager: InternalTaskManager,
    private val connectionLockProvider: ConnectionLockProvider,
) : Layer5Codec {
    private val inboundMessageChannel = Channel<Pair<InboundMessage, ConnectionLock>>(capacity = Channel.UNLIMITED)

    private val isCspConnection = connection is CspConnection

    init {
        CoroutineScope(connectionController.dispatcher.coroutineContext).launch {
            // TODO(ANDR-3579): How does cancellation work here? Will the content of inboundMessageChannel linger
            //  indefinitely if a reconnection/exception happens? In that case, we somehow need to release all ConnectionLocks

            connectionController.cspAuthenticated.await()
            // Start task manager when csp has been authenticated
            taskManager.startRunningTasks(this@EndToEndLayer, incomingMessageProcessor)

            // Forward inbound messages to task manager after it has been started
            inboundMessageChannel.receiveAsFlow().collect { (inboundMessage, lock) ->
                taskManager.processInboundMessage(inboundMessage, lock)
            }
        }
    }

    private val outbound = InputPipe<OutboundL5Message, Unit>()

    override val source: Pipe<OutboundL5Message, Unit> = outbound

    override val sink: PipeHandler<InboundL4Message> = PipeHandler { handleInboundMessage(it) }

    override val closeHandler: PipeCloseHandler<ServerSocketCloseReason> = PipeCloseHandler {
        handleInboundClose(it)
    }

    override fun sendOutbound(message: OutboundMessage) {
        // We check this here to let the task fail immediately. This is required when the connection
        // changes and the task does not check whether MD is still enabled or not.
        if (isCspConnection && message is OutboundD2mMessage) {
            throw IllegalStateException("Cannot send d2m message on csp connection")
        }

        CoroutineScope(outputDispatcher).launch {
            val l5Message = mapMessage(message)
            logger.debug("Send outbound message of type `{}`", l5Message.type)
            outbound.send(l5Message)
        }
    }

    override fun restartConnection(delayMs: Long) {
        CoroutineScope(connectionController.dispatcher.coroutineContext).launch {
            delay(delayMs)
            if (!connectionController.connectionClosed.isCompleted) {
                connection.stop()
                connection.start()
            }
        }
    }

    private fun mapMessage(message: OutboundMessage): OutboundL5Message {
        return when (message) {
            is CspMessage -> message.toCspContainer()
            is OutboundD2mMessage -> message
        }
    }

    private fun handleInboundMessage(message: InboundL4Message) {
        logger.debug("Handle inbound message of type `{}`", message.type)
        // TODO(ANDR-3580): Should we acquire this way earlier, i.e. when receiving bytes on the TCP
        //  layer / a datagram on the WS layer and shove it through the whole pipeline?
        val lock = connectionLockProvider.acquire(60_000, ConnectionLockProvider.ConnectionLogTag.INBOUND_MESSAGE)
        val result = inboundMessageChannel.trySend(
            Pair(
                message.toInboundMessage(),
                lock,
            ),
        )
        if (result.isFailure) {
            logger.error("Unable to forward inbound message to task manager", result.exceptionOrNull())
            lock.release()
        }
    }

    private fun handleInboundClose(closeReason: ServerSocketCloseReason) {
        logger.debug("Handle inbound close: Pausing task manager because of {}", closeReason)
        CoroutineScope(connectionController.dispatcher.coroutineContext).launch {
            taskManager.pauseRunningTasks()
        }
    }
}
