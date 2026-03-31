package ch.threema.storage.factories

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import ch.threema.storage.ColumnIndexCache
import ch.threema.storage.DatabaseProvider
import net.zetetic.database.DatabaseUtils
import net.zetetic.database.sqlcipher.SQLiteDatabase

abstract class ModelFactory internal constructor(
    private val databaseProvider: DatabaseProvider,
    val tableName: String,
) {
    val columnIndexCache = ColumnIndexCache()

    fun deleteAll() {
        writableDatabase.execSQL("DELETE FROM $tableName")
    }

    fun count(): Long = DatabaseUtils.queryNumEntries(readableDatabase, tableName)

    @VisibleForTesting(otherwise = PROTECTED)
    val readableDatabase: SQLiteDatabase
        get() = databaseProvider.readableDatabase

    @VisibleForTesting(otherwise = PROTECTED)
    val writableDatabase: SQLiteDatabase
        get() = databaseProvider.writableDatabase
}
