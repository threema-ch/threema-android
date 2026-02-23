package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.PersistableAppTask

interface AppTaskPersistence {
    /**
     * Persist the [persistableAppTask].
     */
    suspend fun persistTask(persistableAppTask: PersistableAppTask)

    /**
     * Remove the [persistableAppTask]. All persisted tasks with the same serialized data representation will be removed.
     */
    suspend fun removePersistedTask(persistableAppTask: PersistableAppTask)

    /**
     * Load all persisted tasks. Note that this does not remove the tasks from its persisted storage.
     */
    suspend fun loadAllPersistedTasks(): Set<PersistableAppTask>
}
