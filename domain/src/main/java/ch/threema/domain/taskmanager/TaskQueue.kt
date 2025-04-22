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

import ch.threema.domain.protocol.connection.ConnectionLock
import ch.threema.domain.protocol.connection.data.InboundMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

internal class TaskQueue(
    taskArchiver: TaskArchiver,
    private val dispatcherAsserters: TaskManagerImpl.TaskManagerDispatcherAsserters,
) {
    /**
     * A channel that allows the schedule dispatcher to notify the executor dispatcher that a new
     * task (or incoming message) is available. It has a capacity of one and drops the latest
     * channel message if it is full. This guarantees that sending a message to it is not blocking
     * for the sender.
     */
    private val newTaskChannel = Channel<Unit>(1, BufferOverflow.DROP_LATEST)

    /**
     * This queue contains the locally created tasks.
     */
    private val localTaskQueue = LocalTaskQueue(taskArchiver)

    /**
     * This queue contains the task created from inbound messages.
     */
    private var incomingMessageQueue: IncomingMessageTaskQueue? = null

    /**
     * This manager holds connection locks that are kept as long as there are tasks to execute.
     */
    private val connectionLockManager = ConnectionLockManager()

    /**
     * Enqueue a new local task. Note that this method must be called from the schedule dispatcher.
     */
    internal suspend fun <R> enqueueTask(task: Task<R, TaskCodec>, done: CompletableDeferred<R>) {
        dispatcherAsserters.scheduleDispatcher.assertDispatcherContext()

        // Add the task to the local queue
        localTaskQueue.add(task, done)

        // Notify that a new task is available
        newTaskChannel.send(Unit)
    }

    /**
     * Enqueue a new inbound message. Note that this method must be called from the schedule
     * dispatcher.
     */
    internal fun enqueueInboundMessage(
        inboundMessage: InboundMessage,
        connectionLock: ConnectionLock,
    ) = runBlocking {
        dispatcherAsserters.scheduleDispatcher.assertDispatcherContext()

        // Add the connection lock so that it can be released once there are no left tasks
        connectionLockManager.addConnectionLock(connectionLock)

        // Add the inbound message to the incoming message queue
        incomingMessageQueue.get().add(inboundMessage)

        // Notify that a new message (task) is available
        newTaskChannel.send(Unit)
    }

    /**
     * Recreates the incoming message queue. This is needed when the server connection has been re-
     * established. Locally created tasks aren't affected by this.
     */
    internal fun recreateIncomingMessageQueue(incomingMessageProcessor: IncomingMessageProcessor) {
        dispatcherAsserters.executorDispatcher.assertDispatcherContext()

        incomingMessageQueue = IncomingMessageTaskQueue(incomingMessageProcessor)
    }

    /**
     * Read a message from the incoming message queue. Depending on the result of [preProcess], the
     * message is accepted, bypassed, backlogged, or rejected.
     */
    internal suspend fun readMessage(
        preProcess: (InboundMessage) -> MessageFilterInstruction,
        bypassTaskCodec: BypassTaskCodec,
    ): InboundMessage {
        dispatcherAsserters.executorDispatcher.assertDispatcherContext()

        return incomingMessageQueue.get().readMessage(preProcess, bypassTaskCodec)
    }

    /**
     * Return whether there are pending tasks.
     */
    internal fun hasPendingTasks(): Boolean =
        localTaskQueue.hasPendingTasks() || incomingMessageQueue?.hasPendingTasks() ?: false

    /**
     * Get the next task.
     */
    internal suspend fun getNextTask(): TaskQueueElement {
        // Clear the notification from the new task channel as we are processing the task now anyway
        newTaskChannel.tryReceive().getOrNull()

        // Get next task if available
        var queueEntity = poll()
        while (queueEntity == null) {
            // If currently there is no task, release all connection locks
            connectionLockManager.releaseConnectionLocks()
            // Then suspend until a new task has been signaled
            newTaskChannel.receive()
            queueEntity = poll()
        }

        return queueEntity
    }

    /**
     * Get the next task or incoming message if available. Otherwise null is returned.
     */
    private fun poll(): TaskQueueElement? =
        localTaskQueue.getNextOrNull() ?: incomingMessageQueue?.getNextOrNull()

    /**
     * Get the incoming message task queue. Otherwise throws an illegal state exception.
     */
    private fun IncomingMessageTaskQueue?.get(runIfNull: () -> Unit = { }): IncomingMessageTaskQueue {
        if (this == null) {
            runIfNull()
            throw IllegalStateException("Cannot access incoming message queue as it is null")
        }
        return this
    }

    /**
     * A task queue element that defines some properties of the task.
     */
    internal sealed interface TaskQueueElement {
        /**
         * The number of attempts to execute the task. Note that only unexpected exceptions are
         * counted towards this limit.
         */
        val maximumNumberOfExecutions: Int

        /**
         * Run the task.
         */
        suspend fun run(handle: TaskCodec)

        /**
         * Check whether the task is completed.
         */
        fun isCompleted(): Boolean

        /**
         * Complete this task exceptionally. This must only be called, when the
         * [maximumNumberOfExecutions] is reached.
         */
        suspend fun completeExceptionally(exception: Throwable)
    }
}
