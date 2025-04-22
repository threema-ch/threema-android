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

import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = LoggingUtil.getThreemaLogger("LocalTaskQueue")

internal class LocalTaskQueue(private val taskArchiver: TaskArchiver) {
    /**
     * The mutex is required to access [taskQueue].
     */
    private val mutex = Mutex()

    /**
     * The task queue for tasks that have been initiated by this device. Note that this queue should
     * only be accessed when holding the [mutex], as both the scheduler and executor modify this
     * queue.
     */
    private val taskQueue by lazy {
        ArrayDeque<LocalTaskQueueElement<*>>().also { queue ->
            // First load all the persisted tasks and keep them persisted until they are completed
            taskArchiver.loadAllTasks().forEach {
                queue.add(LocalTaskQueueElement(it, CompletableDeferred()))
            }
        }
    }

    internal suspend fun <R> add(task: Task<R, TaskCodec>, done: CompletableDeferred<R>) {
        mutex.withLock {
            taskQueue.add(LocalTaskQueueElement(task, done))
            taskArchiver.addTask(task)
        }
    }

    internal fun getNextOrNull(): TaskQueue.TaskQueueElement? = runBlocking {
        mutex.withLock { taskQueue.firstOrNull() }
    }

    internal fun hasPendingTasks() = runBlocking {
        mutex.withLock { taskQueue.isNotEmpty() }
    }

    /**
     * An element of the local task queue. This is a task that has not been initiated by an incoming
     * message but has been scheduled locally.
     */
    private inner class LocalTaskQueueElement<R>(
        private val task: Task<R, TaskCodec>,
        private val done: CompletableDeferred<R>,
    ) : TaskQueue.TaskQueueElement {
        override val maximumNumberOfExecutions: Int = 5

        override suspend fun run(handle: TaskCodec) {
            logger.info("Running task {}", task.getDebugString())
            done.complete(task.invoke(handle))
            logger.info("Completed task {}", task.getDebugString())
            mutex.withLock {
                taskQueue.remove(this)
                taskArchiver.removeTask(task)
            }
        }

        override fun isCompleted() = done.isCompleted

        override suspend fun completeExceptionally(exception: Throwable) {
            logger.warn("Completing task {} exceptionally", task.getDebugString(), exception)
            done.completeExceptionally(exception)
            mutex.withLock {
                taskQueue.remove(this)
                taskArchiver.removeTask(task)
            }
        }
    }
}
