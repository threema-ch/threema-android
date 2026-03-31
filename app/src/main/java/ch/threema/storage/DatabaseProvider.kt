package ch.threema.storage

import kotlinx.coroutines.flow.StateFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase

interface DatabaseProvider {

    /**
     * Returns a flow that indicates the current state of the database, which can be queried to determine whether [readableDatabase]
     * and [writableDatabase] are safe to call.
     */
    val databaseState: StateFlow<DatabaseState>

    /**
     * Returns a read-only instance of the SQLiteDatabase, or throws an [IllegalStateException] if the database is not currently open.
     */
    val readableDatabase: SQLiteDatabase

    /**
     * Returns a writeable instance of the SQLiteDatabase, or throws an [IllegalStateException] if the database is not currently open.
     */
    val writableDatabase: SQLiteDatabase
}
