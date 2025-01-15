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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.ThreemaApplication
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.managers.CoreServiceManager
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.models.toDataType
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("EmojiReactionsRepository")

class EmojiReactionsRepository(
    private val cache: ModelTypeCache<String, EmojiReactionsModel>,
    private val emojiReactionDao: EmojiReactionsDao,
    private val coreServiceManager: CoreServiceManager,
) {
    private val myIdentity by lazy { coreServiceManager.identityStore.identity }

    // TODO(ANDR-3325): Remove message service
    private val messageService by lazy { ThreemaApplication.requireServiceManager().messageService }

    /**
     * Get all reactions for a message, including reactions from the old ack/dec system
     */
    fun getReactionsByMessage(messageModel: AbstractMessageModel): EmojiReactionsModel? {
        logger.debug("Loading emoji reactions for message {}", messageModel.id)
        synchronized(cache) {
            return cache.getOrCreate(messageModel.uid) {
                val dbReactions = emojiReactionDao.findAllByMessage(messageModel)
                    .map(DbEmojiReaction::toDataType)
                val allReactions = addAckDecReactions(messageModel, dbReactions.toMutableList())
                EmojiReactionsModel(allReactions, coreServiceManager)
            }
        }
    }

    /**
     * Get all reactions for a message, including reactions from the old ack/dec system
     * Returns an empty list if there are no reactions
     */
    fun safeGetReactionsByMessage(messageModel: AbstractMessageModel): List<EmojiReactionData> {
        return getReactionsByMessage(messageModel)?.data?.value ?: emptyList()
    }

    fun deleteAllReactionsForMessage(messageModel: AbstractMessageModel) {
        logger.debug("Delete all for message with uid {}", messageModel.uid)
        emojiReactionDao.deleteAllByMessage(messageModel)
        cache.get(messageModel.uid)?.clear()
        cache.remove(messageModel.uid)
    }

    /**
     * Add reactions from the old ack/dec system to an existing list of emoji reactions
     */
    private fun addAckDecReactions(targetMessageModel: AbstractMessageModel, mutableEmojiReactions: MutableList<EmojiReactionData>): List<EmojiReactionData> {
        if (targetMessageModel is GroupMessageModel) {
            mutableEmojiReactions += targetMessageModel.groupMessageStates?.mapNotNull { (identity, reaction) ->
                when (reaction) {
                    MessageState.USERACK.toString() -> createEmojiReactionData(targetMessageModel, identity, emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE)
                    MessageState.USERDEC.toString() -> createEmojiReactionData(targetMessageModel, identity, emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE)
                    else -> null
                }
            } ?: emptyList()
        } else {
            // in case of an outgoing message only the other party can react - and vice versa
            val senderIdentity = if (targetMessageModel.isOutbox) targetMessageModel.identity else myIdentity
            val state = targetMessageModel.state

            when (state) {
                MessageState.USERACK -> mutableEmojiReactions += createEmojiReactionData(targetMessageModel, senderIdentity, emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE)
                MessageState.USERDEC -> mutableEmojiReactions += createEmojiReactionData(targetMessageModel, senderIdentity, emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE)
                else -> {
                    // ignore
                }
            }
        }
        return mutableEmojiReactions.toList()
    }

    private fun createEmojiReactionData(
        messageModel: AbstractMessageModel,
        senderIdentity: String,
        emojiSequence: String
    ): EmojiReactionData {
        return EmojiReactionData(
            messageModel.id,
            senderIdentity,
            emojiSequence,
            messageModel.createdAt
        )
    }

    /**
     * Inserts a [DbEmojiReaction] into the db.
     *
     * @param targetMessage The message model to create a reaction to
     * @param senderIdentity The identity of the sender of the reaction to create
     * @param emojiSequence The emoji codepoint sequence of the reaction
     *
     * @throws EmojiReactionEntryCreateException if inserting the [DbEmojiReaction] in the database failed
     * @throws IllegalStateException if the [targetMessage] is not valid to create a [DbEmojiReaction] from
     *
     */
    @Throws(EmojiReactionEntryCreateException::class, IllegalStateException::class)
    fun createEntry(
        targetMessage: AbstractMessageModel,
        senderIdentity: String,
        emojiSequence: String
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = Date()
                )
                emojiReactionDao.create(reactionEntry, targetMessage)
                cache.get(targetMessage.uid)?.addEntry(reactionEntry.toDataType())
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryCreateException(exception)
            }
        }
    }

    /**
     * Removes a [DbEmojiReaction] from the db.
     * Call this before saving the edited message
     *
     * @param targetMessage The message model to remove the reaction from
     * @param senderIdentity The identity of the sender of the reaction to remove from the db
     * @param emojiSequence The emoji codepoint sequence of the reaction
     *
     * @throws EmojiReactionEntryRemoveException if removing the [DbEmojiReaction] from the database failed
     * @throws IllegalStateException if the [targetMessage] is not valid
     *
     */
    @Throws(EmojiReactionEntryRemoveException::class, IllegalStateException::class)
    fun removeEntry(
        targetMessage: AbstractMessageModel,
        senderIdentity: String,
        emojiSequence: String
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = Date()
                )

                emojiReactionDao.remove(reactionEntry)

                // TODO(ANDR-3325): Remove ACK/DEC compatibility
                val isThumbsUp = EmojiUtil.isThumbsUpEmoji(emojiSequence)
                val isThumbsDown = EmojiUtil.isThumbsDownEmoji(emojiSequence)

                if (isThumbsUp || isThumbsDown) {
                    // if the existing reaction is an ackji (e.g. received earlier, before we upgraded) and one and more
                    // recipients support emoji reactions, we need to clear the ackji from our database to reflect
                    // the withdraw in the UI (because we sent at least some of the users a WITHDRAW action)
                    //
                    // however, since it is not possible to withdraw ackjis, old clients will not receive a change
                    // which will inadvertently lead to inconsistencies
                    val messageState: MessageState? = targetMessage.state
                    if (messageState != null
                        && ((messageState == MessageState.USERACK && isThumbsUp)
                            || (messageState == MessageState.USERDEC && isThumbsDown))
                    ) {
                        messageService.clearMessageState(targetMessage)
                    }
                }
                cache.get(targetMessage.uid)?.removeEntry(reactionEntry.toDataType())
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryRemoveException(exception)
            }
        }
    }
}

class EmojiReactionEntryCreateException(e: Exception) :
    ThreemaException("Failed to create emoji reaction in the db", e)

class EmojiReactionEntryRemoveException(e: Exception) :
    ThreemaException("Failed to remove emoji reaction from the db", e)
