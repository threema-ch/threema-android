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

package ch.threema.domain.taskmanager

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import kotlinx.coroutines.channels.Channel

private val logger = getThreemaLogger("IncomingMessageTaskQueue")

internal class IncomingMessageTaskQueue(
    private val incomingMessageProcessor: IncomingMessageProcessor,
) {
    /**
     * True if an incoming message task is currently being executed.
     */
    private var hasRunningTask = false

    /**
     * The incoming message queue that holds the messages that have been received. This includes
     * csp, d2m, and d2d messages.
     */
    private val incomingMessageQueue = Channel<IncomingMessageTaskQueueElement>(Channel.UNLIMITED)

    /**
     * Messages from the [incomingMessageQueue] that are not required by the current task may be
     * moved in this queue. Note that this queue does not need any synchronization as only the
     * executor dispatcher accesses it.
     */
    private val backlogQueue = ArrayDeque<IncomingMessageTaskQueueElement>()

    /**
     * Add a new inbound message to the queue.
     */
    internal suspend fun add(inboundMessage: InboundMessage) {
        incomingMessageQueue.send(IncomingMessageTaskQueueElement(inboundMessage))
    }

    /**
     * Get the next task or null.
     */
    internal fun getNextOrNull(): TaskQueue.TaskQueueElement? {
        if (backlogQueue.isNotEmpty()) {
            return backlogQueue.removeFirstOrNull().also { hasRunningTask = it != null }
        }

        return incomingMessageQueue.tryReceive().getOrNull().also {
            hasRunningTask = it != null
        }
    }

    /**
     * Return whether there are pending tasks.
     */
    internal fun hasPendingTasks() = hasRunningTask

    /**
     * Read a message from the task queue. If a message is read that can be bypassed, it is executed
     * directly.
     */
    internal suspend fun readMessage(
        preProcess: (InboundMessage) -> MessageFilterInstruction,
        bypassTaskCodec: BypassTaskCodec,
    ): InboundMessage {
        // First check backlog queue for the matching message
        val backlogIterator = backlogQueue.iterator()
        while (backlogIterator.hasNext()) {
            val backlogMessage = backlogIterator.next()
            when (preProcess(backlogMessage.inboundMessage)) {
                MessageFilterInstruction.ACCEPT -> {
                    backlogIterator.remove()
                    return backlogMessage.inboundMessage
                }

                MessageFilterInstruction.BYPASS_OR_BACKLOG -> {
                    // Assert that messages were only backlogged if they needed to.
                    check(
                        backlogMessage.inboundMessage.getUnsoughtMessageResolution(silent = true)
                            == UnsoughtMessageResolution.BACKLOG,
                    )
                }

                MessageFilterInstruction.REJECT -> TODO("Implement") // TODO(ANDR-2868)
            }
        }

        // Then wait until the matching message arrives in the queue
        while (true) {
            val message = incomingMessageQueue.receive()
            when (preProcess(message.inboundMessage)) {
                MessageFilterInstruction.ACCEPT -> return message.inboundMessage

                MessageFilterInstruction.BYPASS_OR_BACKLOG -> {
                    when (message.inboundMessage.getUnsoughtMessageResolution(silent = false)) {
                        UnsoughtMessageResolution.BYPASS -> message.run(bypassTaskCodec)
                        UnsoughtMessageResolution.BACKLOG -> backlogQueue.add(message)
                    }
                }

                MessageFilterInstruction.REJECT -> TODO("Implement") // TODO(ANDR-2868)
            }
        }
    }

    internal fun flush() {
        var nextIncomingMessage = incomingMessageQueue.tryReceive().getOrNull()
        while (nextIncomingMessage != null) {
            nextIncomingMessage = incomingMessageQueue.tryReceive().getOrNull()
        }

        backlogQueue.clear()
    }

    /**
     * An element of the incoming message queue. This is an element that has been created from an
     * incoming message.
     */
    private inner class IncomingMessageTaskQueueElement(
        val inboundMessage: InboundMessage,
    ) : TaskQueue.TaskQueueElement {
        private val task by lazy {
            when (inboundMessage) {
                is CspMessage -> IncomingCspMessageTask(inboundMessage, incomingMessageProcessor)
                is InboundD2mMessage -> IncomingD2mMessageTask(
                    inboundMessage,
                    incomingMessageProcessor,
                )
            }
        }
        override val maximumNumberOfExecutions: Int = 1

        /**
         * Incoming message task queue elements should be dropped on disconnect. The incoming message task queue will be recreated anyways when a new
         * connection is established. Therefore this flag isn't needed to clean up the queue.
         */
        override val shouldDropOnDisconnect = true

        private var isCompleted = false

        override suspend fun run(handle: TaskCodec) {
            logger.info("Running task {}", task.getDebugString())
            task.invoke(handle)
            isCompleted = true
            logger.info("Completed task {}", task.getDebugString())
            // Note that we do not need to set 'hasRunningTask' to false here. It suffices to set it
            // to false if 'getNextOrNull' returns null.
        }

        override fun isCompleted() = isCompleted

        override suspend fun completeExceptionally(exception: Throwable) {
            logger.warn("Completing task {} exceptionally", task.getDebugString(), exception)
            isCompleted = true
            // Note that we do not need to set 'hasRunningTask' to false here. It suffices to set it
            // to false if 'getNextOrNull' returns null.
        }
    }

    /**
     * Get the result of a message. If [silent] is true, then nothing is logged. Otherwise, the type
     * of the message is logged.
     */
    private fun InboundMessage.getUnsoughtMessageResolution(silent: Boolean) = when (this) {
        /*
         * Reflected messages must be bypassed. This ensures that the state is consistent. When
         * creating a transaction, it is important to apply all reflected messages to ensure the
         * transaction can start safely.
         */
        is InboundD2mMessage.Reflected -> logInfoAndBypass("reflected", silent)

        /*
         * We must not bypass csp messages as they may conflict with the current state that we
         * might be trying to modify.
         */
        is CspMessage -> logInfoAndBacklog("csp", silent)

        /*
         * Receiving a begin transaction ack in a situation where another message is expected cannot
         * happen. Note that we bypass it as processing it at a later moment could lead to a
         * misinterpretation as there is no transaction id that can be used to link the message to
         * a specific transaction.
         */
        is InboundD2mMessage.BeginTransactionAck -> logWarningAndBypass(
            "begin transaction ack",
            silent,
        )

        /*
         * Receiving a commit transaction ack in a situation where another message is expected
         * cannot happen. Note that we bypass it as processing it at a later moment could lead to a
         * misinterpretation as there is no transaction id that can be used to link the message to
         * a specific transaction.
         */
        is InboundD2mMessage.CommitTransactionAck -> logWarningAndBypass(
            "commit transaction ack",
            silent,
        )

        /*
         * Devices info should be backlogged. However, it is unexpected when such a message is being
         * received without expecting it.
         */
        is InboundD2mMessage.DevicesInfo -> logWarningAndBacklog("devices info", silent)

        /*
         * Receiving a drop device ack is unexpected. However, it should be backlogged as it may be
         * needed later on.
         */
        is InboundD2mMessage.DropDeviceAck -> logWarningAndBacklog("drop device ack", silent)

        /*
         * This may happen if several messages were reflected and their acknowledgments are awaited
         * in a different order. We must not bypass this.
         */
        is InboundD2mMessage.ReflectAck -> logInfoAndBacklog("reflect ack", silent)

        /*
         * This can safely be bypassed.
         */
        is InboundD2mMessage.ReflectionQueueDry -> logInfoAndBypass("reflection queue dry", silent)

        /*
         * This can safely be bypassed.
         */
        is InboundD2mMessage.RolePromotedToLeader -> logInfoAndBypass(
            "role promoted to leader",
            silent,
        )

        /*
         * A server hello in the context where another message is received cannot happen.
         */
        is InboundD2mMessage.ServerHello -> logWarningAndBacklog("server hello", silent)

        /*
         * A server info in the context where another message is received cannot happen.
         */
        is InboundD2mMessage.ServerInfo -> logWarningAndBacklog("server info", silent)

        /*
         * A transaction ended message in the context where another message is received should be
         * bypassed as processing it later could lead to a misinterpretation as there is no
         * transaction id that can be used to link the message to a specific transaction.
         */
        is InboundD2mMessage.TransactionEnded -> logInfoAndBypass("transaction ended", silent)

        /*
         * A transaction rejected message in the context where another message is received cannot
         * happen. Note that we bypass it as processing it at a later moment could lead to a
         * misinterpretation as there is no transaction id that can be used to link the message to
         * a specific transaction.
         */
        is InboundD2mMessage.TransactionRejected -> logWarningAndBypass(
            "transaction rejected",
            silent,
        )
    }

    private fun logInfoAndBypass(type: String, silent: Boolean): UnsoughtMessageResolution {
        if (!silent) {
            logger.info("Bypassing a {} message while waiting for another message", type)
        }
        return UnsoughtMessageResolution.BYPASS
    }

    private fun logInfoAndBacklog(type: String, silent: Boolean): UnsoughtMessageResolution {
        if (!silent) {
            logger.info("Backlogging a {} message while waiting for another message", type)
        }
        return UnsoughtMessageResolution.BACKLOG
    }

    private fun logWarningAndBypass(type: String, silent: Boolean): UnsoughtMessageResolution {
        if (!silent) {
            logger.warn("Bypassing a {} message while expecting another message", type)
        }
        return UnsoughtMessageResolution.BYPASS
    }

    private fun logWarningAndBacklog(type: String, silent: Boolean): UnsoughtMessageResolution {
        if (!silent) {
            logger.warn("Backlogging a {} message while expecting another message", type)
        }
        return UnsoughtMessageResolution.BACKLOG
    }

    /**
     * This defines what should happen with messages that are read but not expected.
     */
    private enum class UnsoughtMessageResolution {
        /**
         * The message should be bypassed, so it is run immediately before continuing to wait for
         * the expected message.
         */
        BYPASS,

        /**
         * The message should be backlogged. This means it is kept and applied later on.
         */
        BACKLOG,
    }
}
