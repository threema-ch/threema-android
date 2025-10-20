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

package ch.threema.storage.factories

import ch.threema.storage.DatabaseService
import ch.threema.storage.buildContentValues
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery
import net.zetetic.database.sqlcipher.SQLiteDatabase

class AppTaskPersistenceFactory(databaseService: DatabaseService) : ModelFactory(
    databaseService = databaseService,
    tableName = "app_tasks",
) {

    /**
     * Insert a new app task.
     */
    fun insert(appTaskData: String) {
        val contentValues = buildContentValues { put(COLUMN_APP_TASK_DATA, appTaskData) }
        writableDatabase.insert(
            table = tableName,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_IGNORE,
            values = contentValues,
        )
    }

    /**
     * Remove all app tasks with this string representation.
     */
    fun removeAll(appTaskData: String) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_APP_TASK_DATA = ?",
            whereArgs = arrayOf(appTaskData),
        )
    }

    /**
     * Get all app tasks.
     */
    fun getAll(): Set<String> =
        readableDatabase.runQuery(table = tableName)
            .use {
                buildSet {
                    val appTaskDataColumnIndex = it.getColumnIndex(COLUMN_APP_TASK_DATA)
                    while (it.moveToNext()) {
                        add(it.getString(appTaskDataColumnIndex))
                    }
                }
            }

    override fun getStatements() = arrayOf(
        "CREATE TABLE `$tableName` (" +
            "`$COLUMN_ID` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`$COLUMN_APP_TASK_DATA` TEXT NOT NULL)",
    )

    companion object {
        private const val COLUMN_ID = "id"
        private const val COLUMN_APP_TASK_DATA = "appTaskData"
    }
}
