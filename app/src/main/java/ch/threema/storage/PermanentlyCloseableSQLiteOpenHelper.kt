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
