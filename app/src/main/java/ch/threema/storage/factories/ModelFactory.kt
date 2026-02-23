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
}
