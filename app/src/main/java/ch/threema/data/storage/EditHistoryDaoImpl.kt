/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.data.storage

import androidx.core.database.getStringOrNull
import androidx.sqlite.db.SupportSQLiteOpenHelper
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.EditHistoryEntryCreateException
import ch.threema.storage.buildContentValues
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("EditHistoryDaoImpl")

class EditHistoryDaoImpl(
    private val sqlite: SupportSQLiteOpenHelper,
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

        return sqlite.writableDatabase.insert(
            table = table,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
        )
    }

    override fun deleteAllByMessageUid(messageUid: String) {
        var deletedEntries = sqlite.writableDatabase.delete(
            table = ContactEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        deletedEntries += sqlite.writableDatabase.delete(
            table = GroupEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        logger.debug("{} edit history entries deleted for message {}", deletedEntries, messageUid)
    }

    override fun findAllByMessageUid(messageUid: String): List<DbEditHistoryEntry> {
        val cursor = (sqlite.readableDatabase as SQLiteDatabase)
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
