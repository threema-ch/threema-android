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

package ch.threema.domain.helpers

import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking

class ControlledTaskManager(
    scheduledTaskAssertions: List<(Task<*, TaskCodec>) -> Unit>,
    private val handle: TaskCodec = TransactionAckTaskCodec(),
) : TaskManager {
    private val mutableTaskAssertions = scheduledTaskAssertions.toMutableList()
    val pendingTaskAssertions: List<(Task<*, TaskCodec>) -> Unit> = mutableTaskAssertions

    override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
        // Assert that the task is expected
        if (mutableTaskAssertions.isEmpty()) {
            throw AssertionError("Did not expect a task to be scheduled, but got ${task.type}")
        }
        mutableTaskAssertions.removeAt(0).invoke(task)

        // Run the task and complete the deferred
        val completableDeferred = CompletableDeferred<R>()
        runBlocking {
            completableDeferred.complete(task.invoke(handle))
        }
        return completableDeferred
    }

    override fun hasPendingTasks() = false

    override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }

    override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }
}
