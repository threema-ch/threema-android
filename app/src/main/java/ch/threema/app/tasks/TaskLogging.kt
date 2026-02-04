/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
