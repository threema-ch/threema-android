package ch.threema.app.tasks.archive.recovery

import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec

/**
 * Used for attempting to recover persisted tasks that fail to be decoded. Decoding the task most likely failed because its serializable task has
 * changed. The manager provides different migrations that can create a task from an old task encoding.
 */
fun interface TaskRecoveryManager {
    /**
     * Try to recover an old encoding of a task that could not be decoded. If this succeeds, a task is returned. If it still fails, null is returned.
     */
    fun recoverTask(encodedTask: String): Task<*, TaskCodec>?
}
