/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import ch.threema.storage.ColumnIndexCache
import ch.threema.storage.DatabaseService
import net.zetetic.database.DatabaseUtils
import net.zetetic.database.sqlcipher.SQLiteDatabase

abstract class ModelFactory internal constructor(
    private val databaseService: DatabaseService,
    val tableName: String,
) {
    val columnIndexCache = ColumnIndexCache()

    /**
     * @return the table and index creation statements for a model, used for bootstrapping
     * when the database is created for the first time or recreated.
     */
    abstract fun getStatements(): Array<String>

    fun deleteAll() {
        writableDatabase.execSQL("DELETE FROM $tableName")
    }

    fun count(): Long = DatabaseUtils.queryNumEntries(readableDatabase, tableName)

    @VisibleForTesting(otherwise = PROTECTED)
    val readableDatabase: SQLiteDatabase
        get() = databaseService.readableDatabase

    @VisibleForTesting(otherwise = PROTECTED)
    val writableDatabase: SQLiteDatabase
        get() = databaseService.writableDatabase

    companion object {
        @JvmStatic
        fun noRecordsMessage(id: Any) = "Update of model failed, no records matched for id=$id"
    }
}
