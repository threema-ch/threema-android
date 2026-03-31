package ch.threema.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.databaseupdate.DatabaseUpdate
import ch.threema.storage.databaseupdate.fullDescription
import java.io.File
import kotlin.collections.forEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook

private val logger = getThreemaLogger("DatabaseOpenHelper")

class DatabaseOpenHelper(
    private val appContext: Context,
    databaseName: String? = DEFAULT_DATABASE_NAME_V4,
    password: ByteArray,
    onDatabaseCorrupted: () -> Unit = {},
    private val getCreationStatements: () -> Sequence<String> = DatabaseSchemaCreator::getCreationStatements,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) : PermanentlyCloseableSQLiteOpenHelper(
    context = appContext,
    name = databaseName,
    password = password,
    version = DatabaseUpdater.VERSION,
    errorHandler = DatabaseErrorHandler { sqLiteDatabase: SQLiteDatabase, exception: SQLiteException ->
        logger.error("Database corrupted", exception)

        // close database
        if (sqLiteDatabase.isOpen) {
            try {
                sqLiteDatabase.close()
            } catch (e: Exception) {
                logger.error("Exception while closing database", e)
            }
        }
        onDatabaseCorrupted()
    },
    databaseHook = object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) {
            connection.executeForString("PRAGMA cipher_log_level = NONE;", emptyArray(), null)
            connection.execute("PRAGMA cipher_default_kdf_iter = 1;", emptyArray(), null)
        }

        override fun postKey(connection: SQLiteConnection) {
            connection.execute("PRAGMA kdf_iter = 1;", emptyArray(), null)
        }
    },
    enableWriteAheadLogging = true,
) {
    private val _databaseState = MutableStateFlow(DatabaseState.INIT)
    val databaseState = _databaseState.asStateFlow()

    /**
     * The database schema version prior to the migration (if any), or null if the database was freshly created or has not yet been opened
     */
    var oldVersion: Int? = null
        private set

    override fun onConfigure(db: SQLiteDatabase) {
        oldVersion = db.version.takeUnless { it == 0 }
        db.execSQL("PRAGMA foreign_keys = ON;")
        _databaseState.value = if (oldVersion == DatabaseUpdater.VERSION) {
            DatabaseState.READY
        } else {
            DatabaseState.PREPARING
        }
        super.onConfigure(db)
    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        getCreationStatements().forEach { statement ->
            sqLiteDatabase.execSQL(statement)
        }
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.info("onUpgrade, version {} -> {}", oldVersion, newVersion)

        DatabaseUpdater(appContext, sqLiteDatabase)
            .getUpdates(oldVersion)
            .forEach(::runDatabaseUpdate)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        logger.warn("onDowngrade, version {} -> {}", oldVersion, newVersion)
        throw DatabaseDowngradeException(oldVersion)
    }

    private fun runDatabaseUpdate(databaseUpdate: DatabaseUpdate) {
        logger.info("Running DB update to {}", databaseUpdate.fullDescription)
        try {
            databaseUpdate.run()
        } catch (e: Exception) {
            logger.error("Failed to update database", e)
            throw DatabaseUpdateException(databaseUpdate.version)
        }
    }

    @Throws(DatabaseUpdateException::class, DatabaseDowngradeException::class)
    suspend fun migrateIfNeeded(): Unit = withContext(dispatcherProvider.io) {
        // Open a writeable database, which will trigger the DB migrations, if there are any
        writableDatabase
        _databaseState.value = DatabaseState.READY
    }

    companion object {
        private const val DEFAULT_DATABASE_NAME_V4 = "threema4.db"
        private const val DATABASE_BACKUP_EXT = ".backup"

        @JvmStatic
        fun getDatabaseFile(context: Context): File =
            context.getDatabasePath(DEFAULT_DATABASE_NAME_V4)

        @JvmStatic
        fun getDatabaseBackupFile(context: Context): File =
            context.getDatabasePath(DEFAULT_DATABASE_NAME_V4 + DATABASE_BACKUP_EXT)
    }
}
