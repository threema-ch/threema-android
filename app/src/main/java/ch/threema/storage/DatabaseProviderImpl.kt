package ch.threema.storage

import ch.threema.common.DelegateStateFlow
import ch.threema.common.stateFlowOf
import ch.threema.localcrypto.MasterKey
import kotlinx.coroutines.flow.StateFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseProviderImpl(
    private val databaseOpenHelperFactory: (MasterKey) -> DatabaseOpenHelper,
) : DatabaseProvider {

    private var databaseOpenHelper: DatabaseOpenHelper? = null

    override val readableDatabase: SQLiteDatabase
        get() = databaseOpenHelper?.readableDatabase ?: error("cannot read, DB is closed")
    override val writableDatabase: SQLiteDatabase
        get() = databaseOpenHelper?.writableDatabase ?: error("cannot write, DB is closed")

    private val _databaseState: DelegateStateFlow<DatabaseState> = DelegateStateFlow(stateFlowOf(DatabaseState.INIT))
    override val databaseState: StateFlow<DatabaseState> = _databaseState

    val oldVersion: Int?
        get() = databaseOpenHelper!!.oldVersion

    suspend fun open(masterKey: MasterKey) {
        val databaseOpenHelper = databaseOpenHelperFactory(masterKey)
        _databaseState.delegate = databaseOpenHelper.databaseState
        this.databaseOpenHelper = databaseOpenHelper
        databaseOpenHelper.migrateIfNeeded()
    }

    fun close() {
        _databaseState.delegate = stateFlowOf(DatabaseState.INIT)
        databaseOpenHelper?.close()
        databaseOpenHelper = null
    }
}
