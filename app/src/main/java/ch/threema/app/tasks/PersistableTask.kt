package ch.threema.app.tasks

import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

internal sealed interface PersistableTask {
    fun serialize(): SerializableTaskData?
}

@Serializable
sealed interface SerializableTaskData {
    fun createTask(): Task<*, TaskCodec>
}
