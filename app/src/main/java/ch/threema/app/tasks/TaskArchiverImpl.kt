/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getDebugString
import ch.threema.storage.factories.TaskArchiveFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = LoggingUtil.getThreemaLogger("TaskArchiverImpl")

class TaskArchiverImpl(private val taskArchiveFactory: TaskArchiveFactory) : TaskArchiver {
    private var serviceManager: ServiceManager? = null

    fun setServiceManager(serviceManager: ServiceManager) {
        this.serviceManager = serviceManager
    }

    override fun addTask(task: Task<*, TaskCodec>) {
        task.toEncodedTaskData()?.let {
            logger.info("Persisting task {}", task.getDebugString())
            taskArchiveFactory.insert(it)
        }
    }

    override fun removeTask(task: Task<*, TaskCodec>) {
        val encodedTaskData = task.toEncodedTaskData() ?: return
        logger.info("Removing task {} from archive", task.getDebugString())
        taskArchiveFactory.remove(encodedTaskData)
    }

    override fun loadAllTasks(): List<Task<*, TaskCodec>> {
        // Map all encodings to pairs containing the encoding and the decoded task (or null if
        // decoding failed)
        val tasks = taskArchiveFactory.getAll().map {
            it to it.decodeToTask()
        }

        // Remove all tasks from database that could not be decoded
        tasks.forEach {
            val (encoding, task) = it
            if (task == null) {
                taskArchiveFactory.removeOne(encoding)
                logger.warn("Dropping persisted task as it could not be decoded: '{}'", encoding)
            }
        }

        // Return all successfully decoded tasks
        return tasks.mapNotNull { it.second }.onEach {
            logger.info("Loading task {} from archive", it.getDebugString())
        }
    }

    private fun Task<*, TaskCodec>.toEncodedTaskData(): String? {
        return if (this is PersistableTask) {
            serialize()?.let { Json.encodeToString(it) }
        } else {
            null
        }
    }

    private fun String.decodeToTask(): Task<*, TaskCodec>? {
        val serviceManager = this@TaskArchiverImpl.serviceManager
            ?: throw IllegalStateException("Service manager is not set while loading persisted tasks")

        return try {
            Json.decodeFromString<SerializableTaskData>(this).createTask(serviceManager)
        } catch (e: Exception) {
            logger.error("Task data decoding error. Dropping task '$this'", e)
            null
        }
    }
}
