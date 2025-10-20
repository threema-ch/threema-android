/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.protocol.taskmanager

import ch.threema.base.crypto.NaCl
import ch.threema.domain.helpers.InMemoryContactStore
import ch.threema.domain.helpers.InMemoryIdentityStore
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.connection.ConnectionLock
import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.Pipe
import ch.threema.domain.protocol.connection.PipeCloseHandler
import ch.threema.domain.protocol.connection.PipeHandler
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.data.CspContainer
import ch.threema.domain.protocol.connection.data.CspMessage.ServerAlertData
import ch.threema.domain.protocol.connection.data.CspMessage.ServerErrorData
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundL4Message
import ch.threema.domain.protocol.connection.data.OutboundL5Message
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import ch.threema.domain.taskmanager.SingleThreadedTaskManagerDispatcher
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManagerImpl
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.testhelpers.MUST_NOT_BE_CALLED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * This test tests the bypass mechanism of the task manager. A special task is scheduled that
 * performs a read operation on the task codec and then detects which messages have been bypassed.
 */
class BypassTest {
    enum class MessageAction {
        BYPASS,
        BACKLOG,
    }

    private val noopTaskArchiver: TaskArchiver = object : TaskArchiver {
        override fun addTask(task: Task<*, TaskCodec>) {
            // Nothing to do
        }

        override fun removeTask(task: Task<*, TaskCodec>) {
            // Nothing to do
        }

        override fun loadAllTasks(): List<Task<*, TaskCodec>> = emptyList()
    }

    private val noopDeviceCookieManager: DeviceCookieManager = object : DeviceCookieManager {
        override fun obtainDeviceCookie(): ByteArray = ByteArray(0)

        override fun changeIndicationReceived() {
            // Nothing to do
        }

        override fun deleteDeviceCookie() {
            // Nothing to do
        }
    }

    private val messageCoder: MessageCoder

    init {
        val contactStore = InMemoryContactStore().apply {
            addContact(Contact("01234567", ByteArray(NaCl.PUBLIC_KEY_BYTES)))
        }

        val privateKey = ByteArray(NaCl.SECRET_KEY_BYTES)
        val publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        NaCl.generateKeypairInPlace(publicKey, privateKey)

        val identityStore = InMemoryIdentityStore("TESTTEST", "group", privateKey, "Nickname")

        messageCoder = MessageCoder(contactStore, identityStore)
    }

    @Test
    fun testReflected() = runBlocking {
        val expectation: Map<InboundL4Message, MessageAction> = mapOf(
            InboundD2mMessage.Reflected(0u, 1u, 100u, ByteArray(0)) to MessageAction.BYPASS,
            InboundD2mMessage.Reflected(0u, 2u, 100u, ByteArray(0)) to MessageAction.BYPASS,
        )
        val simulationResult = receiveWhileReading(expectation.keys.toList())

        assertEquals(expectation, simulationResult)
    }

    @Test
    fun testCspMessage() = runBlocking {
        val textMessage = TextMessage().apply {
            fromIdentity = "TESTTEST"
            toIdentity = "01234567"
            text = "Text"
        }
        val encodedMessageBytes =
            messageCoder.encode(textMessage, ByteArray(NaCl.NONCE_BYTES)).makeBinary()

        val expectation: Map<InboundL4Message, MessageAction> = mapOf(
            CspContainer(
                payloadType = ProtocolDefines.PLTYPE_INCOMING_MESSAGE.toUByte(),
                data = encodedMessageBytes,
            ) to MessageAction.BACKLOG,
        )
        val simulationResult = receiveWhileReading(expectation.keys.toList())

        assertEquals(expectation, simulationResult)
    }

    /**
     * Simulate the task manager. A task is run that awaits a message while the provided messages
     * are received. The take message actions (bypass or backlog) will be returned associated by
     * the inbound message.
     */
    private suspend fun receiveWhileReading(inboundMessages: List<InboundL4Message>): Map<InboundL4Message, MessageAction> {
        val loggingIncomingMessageProcessor = LoggingIncomingMessageProcessor()

        val reflectId = 42424242u
        val acceptedReflectAck =
            InboundD2mMessage.ReflectAck(reflectId, 42u)

        return runTask(
            object : Task<Map<InboundL4Message, MessageAction>, TaskCodec> {
                override val type = "TestTask"

                override suspend fun invoke(handle: TaskCodec): Map<InboundL4Message, MessageAction> {
                    // We delay a bit to give the task manager a chance to do a mistake (processing
                    // incoming messages while this task is running).
                    delay(1000)
                    // At this point no message should have been processed so far.
                    assertFalse { loggingIncomingMessageProcessor.hasProcessedMessages() }

                    // As we await the reflect ack, messages will get bypassed and should have been
                    // processed after the ack has been processed.
                    handle.awaitReflectAck(reflectId)

                    return inboundMessages.associateWith { inboundMessage ->
                        if (loggingIncomingMessageProcessor.isInboundMessageProcessed(inboundMessage)) {
                            MessageAction.BYPASS
                        } else {
                            MessageAction.BACKLOG
                        }
                    }
                }
            },
            inboundMessages + acceptedReflectAck,
            loggingIncomingMessageProcessor,
        )
    }

    /**
     * Start the given task, send the provided inbound messages into the end-to-end layer, and then await and return the result of the task.
     */
    private suspend fun <R> runTask(
        task: Task<R, TaskCodec>,
        incomingMessages: List<InboundL4Message>,
        loggingIncomingMessageProcessor: LoggingIncomingMessageProcessor,
    ): R {
        val taskManager = createTaskManager()
        val endToEndLayer = createLayer5Codec(taskManager)

        val input = InputPipe<InboundL4Message, ServerSocketCloseReason>()
        input.pipeInto(endToEndLayer)

        taskManager.startRunningTasks(endToEndLayer, loggingIncomingMessageProcessor)

        val taskResult = taskManager.schedule(task)

        incomingMessages.forEach { message -> input.send(message) }

        return taskResult.await()
    }

    private fun createTaskManager() = TaskManagerImpl(
        { noopTaskArchiver },
        noopDeviceCookieManager,
        TaskManagerImpl.TaskManagerDispatchers(
            SingleThreadedTaskManagerDispatcher(
                assertContext = true,
                threadName = "ExecutorDispatcher",
            ),
            SingleThreadedTaskManagerDispatcher(
                assertContext = true,
                threadName = "ScheduleDispatcher",
            ),
        ),
    )

    private fun createLayer5Codec(taskManager: InternalTaskManager): Layer5Codec =
        object : Layer5Codec {
            override fun sendOutbound(message: OutboundMessage) {
                // Nothing to do
            }

            override fun restartConnection(delayMs: Long) {
                // Nothing to do
            }

            override val source: Pipe<OutboundL5Message, Unit>
                get() = InputPipe()

            override val sink: PipeHandler<InboundL4Message>
                get() = PipeHandler {
                    val lock = object : ConnectionLock {
                        override fun release() {
                            // Ignored
                        }

                        override fun isHeld() = false
                    }
                    taskManager.processInboundMessage(it.toInboundMessage(), lock)
                }

            override val closeHandler: PipeCloseHandler<ServerSocketCloseReason>
                get() = PipeCloseHandler {
                    // Nothing to do
                }
        }

    private class LoggingIncomingMessageProcessor : IncomingMessageProcessor {
        private val processLog: MutableList<ProcessedMessage> = mutableListOf()

        override suspend fun processIncomingCspMessage(
            messageBox: MessageBox,
            handle: ActiveTaskCodec,
        ) {
            processLog.add(ProcessedMessage.CspMessage(messageBox))
        }

        override suspend fun processIncomingD2mMessage(
            message: InboundD2mMessage.Reflected,
            handle: ActiveTaskCodec,
        ) {
            processLog.add(ProcessedMessage.D2mMessage(message))
        }

        override fun processIncomingServerAlert(alertData: ServerAlertData) {
            processLog.add(ProcessedMessage.ServerAlert(alertData))
        }

        override fun processIncomingServerError(errorData: ServerErrorData) {
            processLog.add(ProcessedMessage.ServerError(errorData))
        }

        fun hasProcessedMessages() = processLog.isNotEmpty()

        fun isInboundMessageProcessed(inboundMessage: InboundL4Message): Boolean {
            return processLog.any { processedMessage ->
                processedMessage.correspondsToInboundMessage(
                    inboundMessage,
                )
            }
        }

        sealed interface ProcessedMessage {
            fun correspondsToInboundMessage(inboundL4Message: InboundL4Message): Boolean

            data class CspMessage(private val messageBox: MessageBox) : ProcessedMessage {
                override fun correspondsToInboundMessage(inboundL4Message: InboundL4Message): Boolean {
                    return when (val inboundMessage = inboundL4Message.toInboundMessage()) {
                        is ch.threema.domain.protocol.connection.data.CspMessage -> when (inboundMessage.payloadType.toInt()) {
                            ProtocolDefines.PLTYPE_INCOMING_MESSAGE -> {
                                // We parse the inbound message to get the exact same bytes as the
                                // message processor gets. This way we can compare whether it is the
                                // same message as provided.
                                val parsed = MessageBox.parseBinary(inboundMessage.toIncomingMessageData().data)
                                messageBox.makeBinary().contentEquals(parsed.makeBinary())
                            }

                            else -> false
                        }

                        is InboundD2mMessage -> false
                    }
                }
            }

            data class D2mMessage(private val message: InboundD2mMessage.Reflected) :
                ProcessedMessage {
                override fun correspondsToInboundMessage(inboundL4Message: InboundL4Message): Boolean {
                    return inboundL4Message == message
                }
            }

            data class ServerAlert(private val alertData: ServerAlertData) : ProcessedMessage {
                override fun correspondsToInboundMessage(inboundL4Message: InboundL4Message): Boolean {
                    // Currently we do not test this as server alerts are always handled immediately
                    // and outside of any tasks.
                    MUST_NOT_BE_CALLED()
                }
            }

            data class ServerError(private val serverError: ServerErrorData) : ProcessedMessage {
                override fun correspondsToInboundMessage(inboundL4Message: InboundL4Message): Boolean {
                    // Currently we do not test this as server errors are always handled immediately
                    // and outside of any tasks.
                    MUST_NOT_BE_CALLED()
                }
            }
        }
    }
}
