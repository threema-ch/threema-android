/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

private val logger = LoggingUtil.getThreemaLogger("IncomingMessageTaskQueue")

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
     *
     */
    internal suspend fun readMessage(
        preProcess: (InboundMessage) -> MessageFilterInstruction,
    ): ReadMessageResult {
        val bypassMessages = mutableListOf<IncomingMessageTaskQueueElement>()

        // First check backlog queue for the matching message
        val backlogIterator = backlogQueue.iterator()
        while (backlogIterator.hasNext()) {
            val backlogMessage = backlogIterator.next()
            when (preProcess(backlogMessage.inboundMessage)) {
                MessageFilterInstruction.ACCEPT -> {
                    backlogIterator.remove()
                    return ReadMessageResult(bypassMessages, backlogMessage.inboundMessage)
                }

                // TODO(ANDR-2475): once we bypass messages, no bypassed message should be added to the backlog queue
                MessageFilterInstruction.BYPASS_OR_BACKLOG -> continue

                MessageFilterInstruction.REJECT -> TODO("Implement") // TODO(ANDR-2868)
            }
        }

        // Then wait until the matching message arrives in the queue
        while (true) {
            val message = incomingMessageQueue.receive()
            when (preProcess(message.inboundMessage)) {
                MessageFilterInstruction.ACCEPT -> return ReadMessageResult(
                    bypassMessages,
                    message.inboundMessage
                )

                MessageFilterInstruction.BYPASS_OR_BACKLOG -> {
                    // TODO(ANDR-2475): bypass d2d messages
                    backlogQueue.add(message)
                }

                MessageFilterInstruction.REJECT -> TODO("Implement") // TODO(ANDR-2475)
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
                is InboundD2mMessage -> IncomingD2mMessageTask(inboundMessage)
            }
        }
        private val done: CompletableDeferred<Unit> = CompletableDeferred()

        override val maximumNumberOfExecutions: Int = 1

        override suspend fun run(handle: TaskCodec) {
            logger.info("Running task {}", task.getDebugString())
            done.complete(task.invoke(handle))
            logger.info("Completed task {}", task.getDebugString())
            // Note that we do not need to set 'hasRunningTask' to false here. It suffices to set it
            // to false if 'getNextOrNull' returns null.
        }

        override fun isCompleted() = done.isCompleted

        override suspend fun completeExceptionally(exception: Throwable) {
            logger.warn("Completing task {} exceptionally", task.getDebugString(), exception)
            done.completeExceptionally(exception)
            // Note that we do not need to set 'hasRunningTask' to false here. It suffices to set it
            // to false if 'getNextOrNull' returns null.
        }
    }
}
