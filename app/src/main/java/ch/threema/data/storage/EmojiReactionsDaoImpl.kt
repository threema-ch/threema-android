/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteOpenHelper
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.repositories.EmojiReactionEntryCreateException
import ch.threema.storage.factories.ContactEmojiReactionModelFactory
import ch.threema.storage.factories.GroupEmojiReactionModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = LoggingUtil.getThreemaLogger("EmojiReactionsDaoImpl")

class EmojiReactionsDaoImpl(
    private val sqlite: SupportSQLiteOpenHelper,
) : EmojiReactionsDao {

    override fun create(entry: DbEmojiReaction, messageModel: AbstractMessageModel) {
        val contentValues = ContentValues()

        contentValues.put(DbEmojiReaction.COLUMN_MESSAGE_ID, entry.messageId)
        contentValues.put(DbEmojiReaction.COLUMN_SENDER_IDENTITY, entry.senderIdentity)
        contentValues.put(DbEmojiReaction.COLUMN_EMOJI_SEQUENCE, entry.emojiSequence)
        contentValues.put(DbEmojiReaction.COLUMN_REACTED_AT, entry.reactedAt.time)

        val table = when (messageModel) {
            is MessageModel -> ContactEmojiReactionModelFactory.TABLE
            is GroupMessageModel -> GroupEmojiReactionModelFactory.TABLE
            else -> throw EmojiReactionEntryCreateException(
                IllegalArgumentException("Cannot create reaction entry for message of class ${messageModel.javaClass.name}")
            )
        }
        sqlite.writableDatabase.insert(table, SQLiteDatabase.CONFLICT_ROLLBACK, contentValues)
    }

    override fun remove(entry: DbEmojiReaction) {
        var removedEntries = sqlite.writableDatabase.delete(
                table = GroupEmojiReactionModelFactory.TABLE,
                whereClause = "${DbEmojiReaction.COLUMN_MESSAGE_ID} = ? AND ${DbEmojiReaction.COLUMN_EMOJI_SEQUENCE} = ? AND ${DbEmojiReaction.COLUMN_SENDER_IDENTITY} = ?",
                whereArgs = arrayOf(entry.messageId, entry.emojiSequence, entry.senderIdentity)
            )
        removedEntries += sqlite.writableDatabase.delete(
                table = ContactEmojiReactionModelFactory.TABLE,
                whereClause = "${DbEmojiReaction.COLUMN_MESSAGE_ID} = ? AND ${DbEmojiReaction.COLUMN_EMOJI_SEQUENCE} = ? AND ${DbEmojiReaction.COLUMN_SENDER_IDENTITY} = ?",
                whereArgs = arrayOf(entry.messageId, entry.emojiSequence, entry.senderIdentity)
            )
        logger.debug("{} entries removed for reaction {}", removedEntries, entry)
    }

    override fun deleteAllByMessage(messageModel: AbstractMessageModel) {
        val table = when(messageModel) {
            is GroupMessageModel -> GroupEmojiReactionModelFactory.TABLE
            is MessageModel -> ContactEmojiReactionModelFactory.TABLE
            else -> return
        }

        val deletedEntries = sqlite.writableDatabase.delete(
            table = table,
            whereClause = "${DbEmojiReaction.COLUMN_MESSAGE_ID} = ?",
            whereArgs = arrayOf(messageModel.id)
        )
        logger.debug("{} reaction entries deleted for message {} ({})", deletedEntries, messageModel.id, table)
    }

    override fun findAllByMessage(messageModel: AbstractMessageModel): List<DbEmojiReaction> {
        val table = when (messageModel)  {
            is GroupMessageModel -> GroupEmojiReactionModelFactory.TABLE
            is MessageModel -> ContactEmojiReactionModelFactory.TABLE
            else -> return emptyList()
        }

        val query = "SELECT * FROM $table WHERE ${DbEmojiReaction.COLUMN_MESSAGE_ID} = ? " +
            "ORDER BY ${DbEmojiReaction.COLUMN_REACTED_AT} DESC"

        val cursor = (sqlite.readableDatabase as SQLiteDatabase)
            .rawQuery(
                query,
                messageModel.id
            )

        return cursor.use { getResult(it) }
    }

    private fun getResult(cursor: Cursor) : MutableList<DbEmojiReaction> {
        val result = mutableListOf<DbEmojiReaction>()
        while (cursor.moveToNext()) {
            val messageId = cursor.getInt(
                getColumnIndexOrThrow(
                    cursor,
                    DbEmojiReaction.COLUMN_MESSAGE_ID
                )
            )
            val senderIdentity = cursor.getString(
                getColumnIndexOrThrow(
                    cursor,
                    DbEmojiReaction.COLUMN_SENDER_IDENTITY
                )
            )
            val emojiSequence = cursor.getString(
                getColumnIndexOrThrow(
                    cursor,
                    DbEmojiReaction.COLUMN_EMOJI_SEQUENCE
                )
            )
            val reactedAt = cursor.getDate(
                getColumnIndexOrThrow(
                    cursor,
                    DbEmojiReaction.COLUMN_REACTED_AT
                )
            )

            result.add(
                DbEmojiReaction(
                    messageId = messageId,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = reactedAt
                )
            )
        }

        return result
    }
}
