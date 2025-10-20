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

package ch.threema.storage.factories

import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.DatabaseService
import ch.threema.storage.buildContentValues
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery

private val logger = LoggingUtil.getThreemaLogger("TaskArchiveFactory")

class TaskArchiveFactory(databaseService: DatabaseService) :
    ModelFactory(databaseService, "tasks") {
    companion object {
        private const val COLUMN_ID = "id"
        private const val COLUMN_TASK = "task"
    }

    /**
     * Insert a new task. Note that leading and trailing whitespaces are ignored.
     */
    fun insert(task: String) {
        val contentValues = buildContentValues { put(COLUMN_TASK, task.trim()) }
        writableDatabase.insert(tableName, null, contentValues)
    }

    /**
     * Removes the task that corresponds to this string. Note that leading and trailing whitespaces
     * are ignored. If several tasks with the same string representation exist, only the oldest task
     * is being removed. Note that older tasks are NOT deleted. Use this only to remove invalid
     * tasks. To remove the task including all older tasks, use [remove].
     */
    fun removeOne(task: String) {
        val taskId = getOldestIdForTask(task) ?: return
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_ID=?",
            whereArgs = arrayOf(taskId),
        )
    }

    /**
     * Removes the task that corresponds to this string. Note that leading and trailing whitespaces
     * are ignored. If several tasks with the same string representation exist, only the oldest task
     * is being removed. Note that all older tasks than the given task are deleted as well.
     */
    fun remove(task: String) {
        val taskId = getOldestIdForTask(task) ?: return
        val numDeleted = writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_ID<=?",
            whereArgs = arrayOf(taskId),
        )

        // If several tasks have been deleted, then the just finished task was not the oldest one.
        // This means that we skipped some tasks or have a task reordering!
        if (numDeleted > 1) {
            logger.error(
                "{} instead of 1 tasks were deleted. Some tasks may have been skipped or reordered.",
                numDeleted,
            )
        }
    }

    /**
     * Get all tasks currently stored in the queue. The list is ordered ascending, so that the
     * oldest tasks are first.
     */
    fun getAll(): List<String> =
        readableDatabase.runQuery(tableName, orderBy = "$COLUMN_ID ASC")
            .use {
                buildList {
                    val taskColumnId = it.getColumnIndex(COLUMN_TASK)
                    while (it.moveToNext()) {
                        add(it.getString(taskColumnId))
                    }
                }
            }

    override fun getStatements() = arrayOf(
        "CREATE TABLE `$tableName` (" +
            "`$COLUMN_ID` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`$COLUMN_TASK` STRING NOT NULL)",
    )

    private fun getOldestIdForTask(task: String): Long? {
        readableDatabase.runQuery(
            table = tableName,
            selection = "$COLUMN_TASK=?",
            selectionArgs = arrayOf(task.trim()),
            orderBy = "$COLUMN_ID ASC",
            limit = "1",
        ).use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
            }
        }
        return null
    }
}
