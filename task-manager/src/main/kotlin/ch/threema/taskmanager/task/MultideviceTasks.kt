package ch.threema.taskmanager.task

import kotlinx.coroutines.CoroutineScope

/**
 * Provide the current multi device enablement status
 */
interface MultiDeviceStateProvider {
    val isMultiDeviceEnabled: Boolean
}

/**
 * Denotes a {Task} that is only to be executed when multi device support is enabled.
 */
interface MultiDeviceOnlyTask<R> : MultiDeviceStateProvider, Task<R?> {

    suspend fun executionMultiDeviceOnly(scope: CoroutineScope): R

    override suspend fun invoke(scope: CoroutineScope): R? {
        if (isMultiDeviceEnabled) {
            return executionMultiDeviceOnly(scope)
        }
        return null
    }
}

/**
 * Denotes a {Task} that is only to be executed when multi device support is disabled.
 */
interface MultiDeviceExcludedTask<R> : MultiDeviceStateProvider, Task<R?> {
    suspend fun executionMultiDeviceExcluded(scope: CoroutineScope): R

    override suspend fun invoke(scope: CoroutineScope): R? {
        if (!isMultiDeviceEnabled) {
            return executionMultiDeviceExcluded(scope)
        }
        return null
    }
}

/**
 * Denotes a {Task} that contains actions that do not adhere to the multi device protocol and must be
 * refactored at a later time.
 *
 * This interface must only be used to permit gradual refactorings and user notifications/logging.
 * (i.e. once multi device support is stable, no class may have this interface)
 */
interface MultiDeviceIncapableTask<R> : Task<R>
