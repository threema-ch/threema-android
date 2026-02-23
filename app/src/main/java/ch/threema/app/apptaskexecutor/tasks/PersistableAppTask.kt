package ch.threema.app.apptaskexecutor.tasks

import kotlinx.serialization.Serializable

/**
 * A persistable app task provides a data representation ([AppTaskData]) that is serializable.
 */
interface PersistableAppTask : AppTask {
    /**
     * Serializes the task's state into a persistable format.
     *
     * @return An [AppTaskData] object representing the serialized task.
     */
    fun serialize(): AppTaskData
}

/**
 * Represents the data required to recreate an app task from its persisted state.
 */
@Serializable
sealed interface AppTaskData {
    fun createTask(): PersistableAppTask
}
