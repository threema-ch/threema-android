package ch.threema.app.apptaskexecutor.tasks

/**
 * An app task can be run with the [ch.threema.app.apptaskexecutor.AppTaskExecutor].
 */
interface AppTask {
    /**
     * Run the task.
     */
    suspend fun run()
}
