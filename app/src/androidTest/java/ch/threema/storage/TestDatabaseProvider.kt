package ch.threema.storage

import androidx.test.core.app.ApplicationProvider
import ch.threema.common.stateFlowOf
import net.zetetic.database.sqlcipher.SQLiteDatabase

class TestDatabaseProvider : DatabaseProvider {
    private val inMemoryDatabaseOpenHelper = DatabaseOpenHelper(
        appContext = ApplicationProvider.getApplicationContext(),
        databaseName = null,
        password = "test-database-key".toByteArray(),
    )

    override val databaseState = stateFlowOf(DatabaseState.READY)

    override val readableDatabase: SQLiteDatabase
        get() = inMemoryDatabaseOpenHelper.readableDatabase
    override val writableDatabase: SQLiteDatabase
        get() = inMemoryDatabaseOpenHelper.writableDatabase
}
