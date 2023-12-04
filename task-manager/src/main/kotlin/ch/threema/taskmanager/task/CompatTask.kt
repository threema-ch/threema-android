package ch.threema.taskmanager.task

/**
 * A task that can be run without a coroutine scope.
 */
interface CompatTask<R> : Task<R> {

    /**
     * Run the task immediately.
     */
    fun run(): R

}
