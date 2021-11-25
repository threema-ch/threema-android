package ch.threema.taskmanager

import ch.threema.taskmanager.task.Task
import kotlinx.coroutines.runBlocking

/**
 * Abstract the usage of Task Manager components to be usable from java.
 *
 * Controls dependency injection and the task manager components lifecycle
 */
class TaskManager {
        private val taskQueue: TaskQueue = SimpleThreadSafeTaskQueue()
        private val taskRunner = TaskRunner(taskQueue)

        fun close() = runBlocking {
                taskRunner.close()
        }

        fun queueTask(task: Task<out Any?>) = runBlocking {
                taskQueue.add(task)
        }
}
