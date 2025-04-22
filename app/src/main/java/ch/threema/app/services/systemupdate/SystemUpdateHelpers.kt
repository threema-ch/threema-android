/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services.systemupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Return whether or not the specified fieldName exists in the specified table.
 */
fun fieldExists(
    sqliteDatabase: SQLiteDatabase,
    table: String,
    fieldName: String,
): Boolean {
    // The SQLite table_info pragma returns one row for each normal column in the named table.
    sqliteDatabase.query(
        "pragma_table_info('$table')",
        arrayOf("name"),
        "name = ?",
        arrayOf(fieldName),
        null,
        null,
        null,
    ).use { cursor -> return cursor.count > 0 }
}

fun tableExists(
    sqliteDatabase: SQLiteDatabase,
    table: String,
): Boolean {
    return sqliteDatabase.rawQuery(
        "SELECT 1 FROM `sqlite_master` WHERE type = 'table' AND name = ?",
        arrayOf(table),
    ).use { cursor -> cursor.count > 0 }
}
