package ch.threema.storage.factories

import ch.threema.data.models.GroupModel
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery

class RejectedGroupMessageFactory(databaseProvider: DatabaseProvider) :
    ModelFactory(databaseProvider, TABLE_NAME) {

    fun insertMessageReject(
        rejectedMessageId: MessageId,
        rejectedIdentity: IdentityString,
        groupDatabaseId: Long,
    ) {
        val contentValues = buildContentValues {
            put(COLUMN_MESSAGE_ID, rejectedMessageId.toString())
            put(COLUMN_REJECTED_IDENTITY, rejectedIdentity)
            put(COLUMN_GROUP_ID, groupDatabaseId)
        }
        writableDatabase.insert(tableName, null, contentValues)
    }

    fun getMessageRejects(
        messageId: MessageId,
        groupModel: GroupModel,
    ): Set<String> {
        readableDatabase.runQuery(
            table = tableName,
            selection = "$COLUMN_MESSAGE_ID=? AND $COLUMN_GROUP_ID=?",
            selectionArgs = arrayOf(messageId.toString(), groupModel.getDatabaseId().toString()),
        ).use {
            val rejectedIdentities = mutableSetOf<String>()
            val rejectedIdentityColumnIndex = it.getColumnIndexOrThrow(COLUMN_REJECTED_IDENTITY)
            while (it.moveToNext()) {
                rejectedIdentities.add(it.getString(rejectedIdentityColumnIndex))
            }
            return rejectedIdentities
        }
    }

    fun removeMessageReject(
        rejectedMessageId: MessageId,
        rejectedIdentity: IdentityString,
        groupModel: GroupModel,
    ) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_MESSAGE_ID=? AND $COLUMN_REJECTED_IDENTITY=? AND $COLUMN_GROUP_ID=?",
            whereArgs = arrayOf(rejectedMessageId.toString(), rejectedIdentity, groupModel.getDatabaseId()),
        )
    }

    fun removeAllMessageRejectsInGroup(group: GroupModel) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_GROUP_ID=?",
            whereArgs = arrayOf(group.getDatabaseId()),
        )
    }

    fun removeMessageRejectByGroupAndIdentity(group: GroupModel, identity: IdentityString) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "$COLUMN_REJECTED_IDENTITY=? AND $COLUMN_GROUP_ID=?",
            whereArgs = arrayOf(identity, group.getDatabaseId()),
        )
    }

    object Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `$TABLE_NAME` (" +
                "`${COLUMN_MESSAGE_ID}` INTEGER NOT NULL, " +
                "`${COLUMN_REJECTED_IDENTITY}` VARCHAR NOT NULL, " +
                "`${COLUMN_GROUP_ID}` INTEGER NOT NULL, " +
                "PRIMARY KEY (`${COLUMN_MESSAGE_ID}`, `${COLUMN_REJECTED_IDENTITY}`, `${COLUMN_GROUP_ID}`) ON CONFLICT IGNORE " +
                ")",
        )
    }

    companion object {
        private const val TABLE_NAME = "rejected_group_messages"
        private const val COLUMN_MESSAGE_ID = "messageId"
        private const val COLUMN_REJECTED_IDENTITY = "rejectedIdentity"
        private const val COLUMN_GROUP_ID = "groupId"
    }
}
