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

import android.content.Context
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

abstract class PermanentlyCloseableSQLiteOpenHelper(
    context: Context,
    name: String?,
    private val password: ByteArray,
    version: Int,
    errorHandler: DatabaseErrorHandler?,
    databaseHook: SQLiteDatabaseHook,
    enableWriteAheadLogging: Boolean,
) : SQLiteOpenHelper(
    context,
    name,
    password,
    null,
    version,
    0,
    errorHandler,
    databaseHook,
    enableWriteAheadLogging,
) {
    private var isClosed = false

    override val readableDatabase: SQLiteDatabase
        get() = synchronized(this) {
            ensureNotClosed()
            super.readableDatabase
        }

    override val writableDatabase: SQLiteDatabase
        get() = synchronized(this) {
            ensureNotClosed()
            super.writableDatabase
        }

    private fun ensureNotClosed() {
        if (isClosed) {
            error("Database is already closed")
        }
    }

    override fun close() = synchronized(this) {
        isClosed = true
        super.close()
        password.fill(0)
    }
}
