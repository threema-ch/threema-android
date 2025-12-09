/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
import android.database.sqlite.SQLiteException
import androidx.annotation.OpenForTesting
import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.databaseupdate.*
import ch.threema.storage.factories.AppTaskPersistenceFactory
import ch.threema.storage.factories.BallotChoiceModelFactory
import ch.threema.storage.factories.BallotModelFactory
import ch.threema.storage.factories.BallotVoteModelFactory
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.ContactEmojiReactionModelFactory
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.ConversationTagFactory
import ch.threema.storage.factories.DistributionListMemberModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.DistributionListModelFactory
import ch.threema.storage.factories.EditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupBallotModelFactory
import ch.threema.storage.factories.GroupCallModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEmojiReactionModelFactory
import ch.threema.storage.factories.GroupMemberModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.GroupModelFactory
import ch.threema.storage.factories.IdentityBallotModelFactory
import ch.threema.storage.factories.IncomingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.factories.OutgoingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.RejectedGroupMessageFactory
import ch.threema.storage.factories.ServerMessageModelFactory
import ch.threema.storage.factories.TaskArchiveFactory
import ch.threema.storage.factories.WebClientSessionModelFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook

private val logger = getThreemaLogger("DatabaseService")

@OpenForTesting
@SessionScoped
open class DatabaseService
@JvmOverloads
constructor(
    private val context: Context,
    databaseName: String? = DEFAULT_DATABASE_NAME_V4,
    password: ByteArray,
    onDatabaseCorrupted: () -> Unit = {},
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) : PermanentlyCloseableSQLiteOpenHelper(
    context = context,
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

    val contactModelFactory: ContactModelFactory by lazy {
        ContactModelFactory(this)
    }
    val messageModelFactory: MessageModelFactory by lazy {
        MessageModelFactory(this)
    }
    val groupModelFactory: GroupModelFactory by lazy {
        GroupModelFactory(this)
    }
    val groupMemberModelFactory: GroupMemberModelFactory by lazy {
        GroupMemberModelFactory(this)
    }
    val groupMessageModelFactory: GroupMessageModelFactory by lazy {
        GroupMessageModelFactory(this)
    }
    val distributionListModelFactory: DistributionListModelFactory by lazy {
        DistributionListModelFactory(this)
    }
    val distributionListMemberModelFactory: DistributionListMemberModelFactory by lazy {
        DistributionListMemberModelFactory(this)
    }
    val distributionListMessageModelFactory: DistributionListMessageModelFactory by lazy {
        DistributionListMessageModelFactory(this)
    }
    val outgoingGroupSyncRequestLogModelFactory: OutgoingGroupSyncRequestLogModelFactory by lazy {
        OutgoingGroupSyncRequestLogModelFactory(this)
    }
    val incomingGroupSyncRequestLogModelFactory: IncomingGroupSyncRequestLogModelFactory by lazy {
        IncomingGroupSyncRequestLogModelFactory(this)
    }
    val ballotModelFactory: BallotModelFactory by lazy {
        BallotModelFactory(this)
    }
    val ballotChoiceModelFactory: BallotChoiceModelFactory by lazy {
        BallotChoiceModelFactory(this)
    }
    val ballotVoteModelFactory: BallotVoteModelFactory by lazy {
        BallotVoteModelFactory(this)
    }
    val identityBallotModelFactory: IdentityBallotModelFactory by lazy {
        IdentityBallotModelFactory(this)
    }
    val groupBallotModelFactory: GroupBallotModelFactory by lazy {
        GroupBallotModelFactory(this)
    }
    val webClientSessionModelFactory: WebClientSessionModelFactory by lazy {
        WebClientSessionModelFactory(this)
    }
    val conversationTagFactory: ConversationTagFactory by lazy {
        ConversationTagFactory(this)
    }
    val groupCallModelFactory: GroupCallModelFactory by lazy {
        GroupCallModelFactory(this)
    }
    val serverMessageModelFactory: ServerMessageModelFactory by lazy {
        ServerMessageModelFactory(this)
    }
    val taskArchiveFactory: TaskArchiveFactory by lazy {
        TaskArchiveFactory(this)
    }
    val appTaskPersistenceFactory: AppTaskPersistenceFactory by lazy {
        AppTaskPersistenceFactory(this)
    }
    val rejectedGroupMessageFactory: RejectedGroupMessageFactory by lazy {
        RejectedGroupMessageFactory(this)
    }
    private val contactEditHistoryEntryModelFactory: EditHistoryEntryModelFactory by lazy {
        ContactEditHistoryEntryModelFactory(this)
    }
    private val groupEditHistoryEntryModelFactory: EditHistoryEntryModelFactory by lazy {
        GroupEditHistoryEntryModelFactory(this)
    }
    private val contactEmojiReactionModelFactory: ContactEmojiReactionModelFactory by lazy {
        ContactEmojiReactionModelFactory(this)
    }
    private val groupEmojiReactionModelFactory: GroupEmojiReactionModelFactory by lazy {
        GroupEmojiReactionModelFactory(this)
    }

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
        arrayOf(
            contactModelFactory,
            messageModelFactory,
            groupModelFactory,
            groupMemberModelFactory,
            groupMessageModelFactory,
            distributionListModelFactory,
            distributionListMemberModelFactory,
            distributionListMessageModelFactory,
            outgoingGroupSyncRequestLogModelFactory,
            incomingGroupSyncRequestLogModelFactory,
            ballotModelFactory,
            ballotChoiceModelFactory,
            ballotVoteModelFactory,
            identityBallotModelFactory,
            groupBallotModelFactory,
            webClientSessionModelFactory,
            conversationTagFactory,
            groupCallModelFactory,
            serverMessageModelFactory,
            taskArchiveFactory,
            appTaskPersistenceFactory,
            rejectedGroupMessageFactory,
            contactEditHistoryEntryModelFactory,
            groupEditHistoryEntryModelFactory,
            contactEmojiReactionModelFactory,
            groupEmojiReactionModelFactory,
        ).forEach { factory ->
            for (statement in factory.getStatements()) {
                sqLiteDatabase.execSQL(statement)
            }
        }
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.info("onUpgrade, version {} -> {}", oldVersion, newVersion)

        DatabaseUpdater(context, sqLiteDatabase)
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
            throw DatabaseUpdateException(databaseUpdate.getVersion())
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
