package ch.threema.domain.taskmanager

/**
 * This interface defines the functionality of a task archiver to achieve persistence.
 */
interface TaskArchiver {
    /**
     * Add another task to the archive.
     */
    fun addTask(task: Task<*, TaskCodec>)

    /**
     * Remove the given task from the archive. Note that if we have several archived tasks that
     * cannot be distinguished from each other, then we should only delete the oldest archived task.
     */
    fun removeTask(task: Task<*, TaskCodec>)

    /**
     * Load all tasks in the same order they have been archived.
     */
    fun loadAllTasks(): List<Task<*, TaskCodec>>
}
