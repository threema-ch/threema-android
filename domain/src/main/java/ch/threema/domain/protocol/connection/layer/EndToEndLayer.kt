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

import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.Pipe
import ch.threema.domain.protocol.connection.PipeHandler
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundL4Message
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundL5Message
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private val logger = ConnectionLoggingUtil.getConnectionLogger("EndToEndLayer")

internal class EndToEndLayer(
    private val outputDispatcher: CoroutineContext,
    private val connectionController: ServerConnectionController,
    private val connection: ServerConnection,
    private val incomingMessageProcessor: IncomingMessageProcessor,
    private val taskManager: InternalTaskManager,
) : Layer5Codec {
    private val inboundMessageChannel = Channel<InboundMessage>(capacity = Channel.UNLIMITED)

    init {
        CoroutineScope(connectionController.dispatcher.coroutineContext).launch {
            launch {
                connectionController.cspAuthenticated.await()
                // Start task manager when csp has been authenticated
                taskManager.startRunningTasks(this@EndToEndLayer, incomingMessageProcessor)

                // Forward inbound messages to task manager after it has been started
                inboundMessageChannel.receiveAsFlow().collect {
                    taskManager.processInboundMessage(it)
                }
            }

            launch {
                connectionController.connectionClosed.await()
                taskManager.pauseRunningTasks()
            }
        }
    }

    private val outbound = InputPipe<OutboundL5Message>()

    override val source: Pipe<OutboundL5Message> = outbound

    override val sink: PipeHandler<InboundL4Message> = PipeHandler { handleInboundMessage(it) }

    override fun sendOutbound(message: OutboundMessage) {
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
        inboundMessageChannel.trySend(message.toInboundMessage())
    }
}
