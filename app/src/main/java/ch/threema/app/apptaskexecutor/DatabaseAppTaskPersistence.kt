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

package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.AppTaskData
import ch.threema.app.apptaskexecutor.tasks.PersistableAppTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.factories.AppTaskPersistenceFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = LoggingUtil.getThreemaLogger("DatabaseAppTaskPersistence")

class DatabaseAppTaskPersistence(private val appTaskPersistenceFactory: AppTaskPersistenceFactory) : AppTaskPersistence {

    override suspend fun persistTask(persistableAppTask: PersistableAppTask) = mutex.withLock {
        val encodedPersistableAppTaskData = encodePersistableAppTask(persistableAppTask)
        if (encodedPersistableAppTaskData != null) {
            appTaskPersistenceFactory.insert(encodedPersistableAppTaskData)
        } else {
            logger.error("Could not persist app task data")
        }
    }

    override suspend fun removePersistedTask(persistableAppTask: PersistableAppTask) = mutex.withLock {
        val encodedPersistableAppTaskData = encodePersistableAppTask(persistableAppTask)
        if (encodedPersistableAppTaskData != null) {
            appTaskPersistenceFactory.removeAll(encodedPersistableAppTaskData)
        } else {
            logger.error("Could not remove persisted app task data")
        }
    }

    override suspend fun loadAllPersistedTasks(): Set<PersistableAppTask> = mutex.withLock {
        return appTaskPersistenceFactory.getAll().mapNotNull(this::decodePersistableAppTaskData).toSet()
    }

    private fun encodePersistableAppTask(persistableAppTask: PersistableAppTask): String? = try {
        Json.encodeToString(persistableAppTask.serialize()).trim()
    } catch (exception: Exception) {
        logger.error("Could not encode persistable app task", exception)
        null
    }

    private fun decodePersistableAppTaskData(encodedPersistableAppTask: String): PersistableAppTask? =
        try {
            Json.decodeFromString<AppTaskData>(encodedPersistableAppTask).createTask()
        } catch (exception: Exception) {
            logger.error("Could not decode persisted app task", exception)
            null
        }

    companion object {
        private val mutex = Mutex()
    }
}
