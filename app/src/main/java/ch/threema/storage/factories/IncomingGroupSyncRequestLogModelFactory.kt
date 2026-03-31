package ch.threema.storage.factories

import android.database.Cursor
import android.database.SQLException
import ch.threema.domain.types.IdentityString
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.models.IncomingGroupSyncRequestLogModel
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.storage.runQuery
import net.zetetic.database.sqlcipher.SQLiteDatabase

class IncomingGroupSyncRequestLogModelFactory(databaseProvider: DatabaseProvider) :
    ModelFactory(databaseProvider, IncomingGroupSyncRequestLogModel.TABLE) {
    /**
     * Insert the provided model into the database.
     *
     * @throws [SQLException] if the provided local db group id is not valid
     */
    @Synchronized
    fun createOrUpdate(groupSyncRequestLog: IncomingGroupSyncRequestLogModel) {
        writableDatabase.insert(
            tableName,
            SQLiteDatabase.CONFLICT_REPLACE,
            groupSyncRequestLog.toContentValues(),
        )
    }

    /**
     * Get an [IncomingGroupSyncRequestLogModel] by [localDbGroupId] and [senderIdentity]. If there
     * is no such entry in the database, a new model is returned where
     * [IncomingGroupSyncRequestLogModel.lastHandledRequest] is set to 0.
     */
    @Synchronized
    fun getByGroupIdAndSenderIdentity(
        localDbGroupId: Long,
        senderIdentity: IdentityString,
    ): IncomingGroupSyncRequestLogModel {
        readableDatabase.runQuery(
            table = tableName,
            selection = "${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID} = ? AND ${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY} = ?",
            selectionArgs = arrayOf(localDbGroupId.toString(), senderIdentity),
        )
            .use {
                return if (it.moveToFirst()) {
                    it.toGroupSyncRequestLogModel()
                } else {
                    IncomingGroupSyncRequestLogModel(localDbGroupId, senderIdentity, 0)
                }
            }
    }

    private fun Cursor.toGroupSyncRequestLogModel(): IncomingGroupSyncRequestLogModel {
        val groupIdColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID)
        val senderIdentityColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY)
        val lastHandledRequestColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST)

        return IncomingGroupSyncRequestLogModel(
            getLong(groupIdColumnIndex),
            getString(senderIdentityColumnIndex),
            getLong(lastHandledRequestColumnIndex),
        )
    }

    private fun IncomingGroupSyncRequestLogModel.toContentValues() = buildContentValues {
        put(IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID, groupId)
        put(IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY, senderIdentity)
        put(IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST, lastHandledRequest)
    }

    object Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            """
                CREATE TABLE `${IncomingGroupSyncRequestLogModel.TABLE}`(
                    `${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}` INTEGER,
                    `${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY}` VARCHAR,
                    `${IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST}` DATETIME,
                    PRIMARY KEY (`${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}`, `${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY}`),
                    FOREIGN KEY(`${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}`) REFERENCES `${GroupModelOld.TABLE}`(`${GroupModelOld.COLUMN_ID}`) ON UPDATE CASCADE ON DELETE CASCADE
                )
            """.trimIndent(),
        )
    }
}
