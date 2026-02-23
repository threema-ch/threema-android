package ch.threema.app.tasks.archive.recovery

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec

/**
 * A handler that tries to recover a task that cannot be decoded regularly.
 */
fun interface TaskRecoveryHandler {
    /**
     * Try recovering the [encodedTask]. If this succeeds, the task is returned. Otherwise this method returns null.
     */
    fun tryRecovery(encodedTask: String, serviceManager: ServiceManager): Task<*, TaskCodec>?
}
