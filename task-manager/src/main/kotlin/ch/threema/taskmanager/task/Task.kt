package ch.threema.taskmanager.task

import kotlinx.coroutines.CoroutineScope

/**
 * A Task that executes a specific application action according to a complex execution plan.
 */
interface Task<R> {
    /**
     * Complex execution plan for this task.
     */
    suspend fun invoke(scope: CoroutineScope): R
}
