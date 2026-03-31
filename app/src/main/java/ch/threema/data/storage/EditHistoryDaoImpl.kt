package ch.threema.data.storage

import androidx.core.database.getStringOrNull
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.EditHistoryEntryCreateException
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupMessageModel
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("EditHistoryDaoImpl")

class EditHistoryDaoImpl(
    private val databaseProvider: DatabaseProvider,
) : EditHistoryDao {
    override fun create(entry: DbEditHistoryEntry, messageModel: AbstractMessageModel): Long {
        val contentValues = buildContentValues {
            put(DbEditHistoryEntry.COLUMN_MESSAGE_UID, entry.messageUid)
            put(DbEditHistoryEntry.COLUMN_MESSAGE_ID, entry.messageId)
            put(DbEditHistoryEntry.COLUMN_TEXT, entry.text)
            put(DbEditHistoryEntry.COLUMN_EDITED_AT, entry.editedAt.time)
        }

        val table = when (messageModel) {
            is MessageModel -> ContactEditHistoryEntryModelFactory.TABLE
            is GroupMessageModel -> GroupEditHistoryEntryModelFactory.TABLE
            else -> throw EditHistoryEntryCreateException(
                IllegalArgumentException("Cannot create edit history entry for message of class ${messageModel.javaClass.name}"),
            )
        }

        return databaseProvider.writableDatabase.insert(
            table = table,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
        )
    }

    override fun deleteAllByMessageUid(messageUid: String) {
        var deletedEntries = databaseProvider.writableDatabase.delete(
            table = ContactEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        deletedEntries += databaseProvider.writableDatabase.delete(
            table = GroupEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        logger.debug("{} edit history entries deleted for message {}", deletedEntries, messageUid)
    }

    override fun findAllByMessageUid(messageUid: String): List<DbEditHistoryEntry> {
        val cursor = databaseProvider.readableDatabase
            .rawQuery(
                "SELECT * FROM ${ContactEditHistoryEntryModelFactory.TABLE} WHERE ${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ? " +
                    "UNION " +
                    "SELECT * FROM ${GroupEditHistoryEntryModelFactory.TABLE} WHERE ${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ? " +
                    "ORDER BY ${DbEditHistoryEntry.COLUMN_EDITED_AT} DESC",
                messageUid,
                messageUid,
            )

        val result = mutableListOf<DbEditHistoryEntry>()
        while (cursor.moveToNext()) {
            val uid = cursor.getInt(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_UID))
            val messageId =
                cursor.getInt(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_MESSAGE_ID))
            val text = cursor.getStringOrNull(
                getColumnIndexOrThrow(
                    cursor,
                    DbEditHistoryEntry.COLUMN_TEXT,
                ),
            )
            val editedAt =
                cursor.getDate(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_EDITED_AT))
            result.add(
                DbEditHistoryEntry(
                    uid = uid,
                    messageUid = messageUid,
                    messageId = messageId,
                    text = text,
                    editedAt = editedAt,
                ),
            )
        }

        cursor.close()
        return result
    }
}
