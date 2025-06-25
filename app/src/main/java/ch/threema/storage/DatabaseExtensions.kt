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

package ch.threema.storage

import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase

fun SQLiteDatabase.runQuery(
    table: String,
    columns: Array<String>? = null,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    groupBy: String? = null,
    having: String? = null,
    orderBy: String? = null,
    limit: String? = null,
): Cursor =
    query(
        table,
        columns,
        selection,
        selectionArgs,
        groupBy,
        having,
        orderBy,
        limit,
    )

fun SQLiteDatabase.fieldExists(
    table: String,
    fieldName: String,
): Boolean =
    // The SQLite table_info pragma returns one row for each normal column in the named table.
    runQuery(
        table = "pragma_table_info('$table')",
        columns = arrayOf("name"),
        selection = "name = ?",
        selectionArgs = arrayOf(fieldName),
    )
        .use { cursor -> cursor.count > 0 }

fun SQLiteDatabase.tableExists(
    table: String,
): Boolean =
    rawQuery(
        "SELECT 1 FROM `sqlite_master` WHERE type = 'table' AND name = ?",
        arrayOf(table),
    )
        .use { cursor -> cursor.count > 0 }
