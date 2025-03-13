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

import android.database.sqlite.SQLiteException
import ch.threema.app.utils.ThrowingConsumer
import ch.threema.storage.models.AbstractMessageModel

interface EmojiReactionsDao {
    /**
     * Insert a new emoji reaction
     *
     * @param entry The entry to add for the reaction
     * @param messageModel The message referenced by the reaction entry
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws ch.threema.data.repositories.EmojiReactionEntryCreateException if inserting the [DbEmojiReaction] in the database failed
     */
    fun create(entry: DbEmojiReaction, messageModel: AbstractMessageModel)

    /**
     * Remove an emoji reaction from the database
     */
    fun remove(entry: DbEmojiReaction, messageModel: AbstractMessageModel)

    /**
     * Delete all reactions referred to by the specified message id
     */
    fun deleteAllByMessage(messageModel: AbstractMessageModel)

    /**
     * Find all reactions referred to by the specified message id
     */
    fun findAllByMessage(messageModel: AbstractMessageModel): List<DbEmojiReaction>

    /**
     * Delete all reactions from the database.
     */
    fun deleteAll()

    fun getContactReactionsCount(): Long

    fun getGroupReactionsCount(): Long

    /**
     * Iteration is ordered by the id of the referenced messages.
     */
    fun iterateAllContactBackupReactions(consumer: ThrowingConsumer<BackupContactReaction>)

    /**
     * Iteration is ordered by the id of the referenced messages.
     */
    fun iterateAllGroupBackupReactions(consumer: ThrowingConsumer<BackupGroupReaction>)

    fun insertContactReactionsInTransaction(block: TransactionalReactionInsertScope)

    fun insertGroupReactionsInTransaction(block: TransactionalReactionInsertScope)

    data class BackupContactReaction(
        val contactIdentity: String,
        val apiMessageId: String,
        val senderIdentity: String,
        val emojiSequence: String,
        val reactedAt: Long
    )

    data class BackupGroupReaction(
        val apiGroupId: String,
        val groupCreatorIdentity: String,
        val apiMessageId: String,
        val senderIdentity: String,
        val emojiSequence: String,
        val reactedAt: Long
    )

    fun interface ReactionInsertHandle {
        fun insert(entry: DbEmojiReaction)
    }

    fun interface TransactionalReactionInsertScope {
        @Throws(Exception::class)
        fun runInserts(handle: ReactionInsertHandle)
    }
}
