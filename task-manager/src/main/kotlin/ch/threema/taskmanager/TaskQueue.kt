package ch.threema.taskmanager

import ch.threema.taskmanager.task.Task
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TaskQueue {
    val newTaskObservers: MutableList<suspend (Task<out Any?>) -> Unit>

    suspend fun removeFirstOrNull(): Task<out Any?>?
    suspend fun add(element: Task<out Any?>)
}

/**
 * Thread-Safe Task Queue
 */
class SimpleThreadSafeTaskQueue : TaskQueue {
    private val mutex = Mutex()
    private val taskQueue = ArrayDeque<Task<out Any?>>()

    /**
     * Subscribe to new Tasks.
     *
     * Note that the notification is not executed in a locked setting, i.e. a Task might already be
     * removed from the queue before subscribers are notified.
     */
    override val newTaskObservers = mutableListOf<suspend (Task<out Any?>) -> Unit>()

    /**
     * Removes the first element from this queue and returns that removed element,
     * or returns null if this queue is empty.
     */
    override suspend fun removeFirstOrNull(): Task<out Any?>? = mutex.withLock {
        taskQueue.removeFirstOrNull()
    }

    /**
     * Adds the specified element to the end of this queue and notifies all observers.
     */
    override suspend fun add(element: Task<out Any?>) {
        mutex.withLock {
            taskQueue.add(element)
        }
        newTaskObservers.forEach { it(element) }
    }
}
