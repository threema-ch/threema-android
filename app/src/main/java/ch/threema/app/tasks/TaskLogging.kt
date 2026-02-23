package ch.threema.app.tasks

import ch.threema.domain.taskmanager.Task
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Get the task as debug string. This is only used for debugging.
 */
val getDebugString: Task<*, *>.() -> String = {
    val serializedHashCode = if (this is PersistableTask) {
        serialize()?.let { serializedTaskData ->
            Json.encodeToString(serializedTaskData).trim().hashCode()
        }
    } else {
        null
    }
    val taskInstanceHashCode = hashCode()
    buildString {
        append(type)
        if (shortLogInfo != null) {
            append("[")
            append(shortLogInfo)
            append("]")
        }
        append("@")
        append(taskInstanceHashCode)
        if (serializedHashCode != null) {
            append("~")
            append(serializedHashCode)
        }
    }
}
