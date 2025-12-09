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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.services.MessageService
import ch.threema.app.utils.ThrowingConsumer
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.models.toDataType
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import org.koin.mp.KoinPlatform

private val logger = getThreemaLogger("EmojiReactionsRepository")

@SessionScoped
class EmojiReactionsRepository(
    private val cache: ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>,
    private val emojiReactionDao: EmojiReactionsDao,
    private val coreServiceManager: CoreServiceManager,
) {
    private val myIdentity by lazy { coreServiceManager.identityStore.getIdentity()!! }

    // TODO(ANDR-3325): Remove message service
    private val messageService: MessageService by KoinPlatform.getKoin().inject()

    /**
     * Get all reactions for a message, including reactions from the old ack/dec system
     */
    fun getReactionsByMessage(messageModel: AbstractMessageModel): EmojiReactionsModel? {
        logger.debug("Loading emoji reactions for message {}", messageModel.id)
        synchronized(cache) {
            val reactionMessageIdentifier =
                ReactionMessageIdentifier.fromMessageModel(messageModel) ?: return null
            return cache.getOrCreate(reactionMessageIdentifier) {
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
        return getReactionsByMessage(messageModel)?.data ?: emptyList()
    }

    fun deleteAllReactionsForMessage(messageModel: AbstractMessageModel) {
        logger.debug("Delete all for message with uid {}", messageModel.uid)
        emojiReactionDao.deleteAllByMessage(messageModel)
        ReactionMessageIdentifier.fromMessageModel(messageModel)?.let { reactionMessageIdentifier ->
            cache.get(reactionMessageIdentifier)?.clear()
            cache.remove(reactionMessageIdentifier)
        }
    }

    /**
     * This deletes all reactions from the database.
     */
    fun deleteAllReactions() {
        emojiReactionDao.deleteAll()
    }

    fun getContactReactionsCount(): Long {
        return emojiReactionDao.getContactReactionsCount()
    }

    fun getGroupReactionsCount(): Long {
        return emojiReactionDao.getGroupReactionsCount()
    }

    /**
     * This method is only intended for backup creation.
     * Iteration is ordered by id of the referenced messages.
     */
    fun iterateAllContactReactionsForBackup(consumer: ThrowingConsumer<EmojiReactionsDao.BackupContactReaction>) {
        emojiReactionDao.iterateAllContactBackupReactions(consumer)
    }

    /**
     * This method is only intended for backup creation.
     * Iteration is ordered by id of the referenced messages.
     */
    fun iterateAllGroupReactionsForBackup(consumer: ThrowingConsumer<EmojiReactionsDao.BackupGroupReaction>) {
        emojiReactionDao.iterateAllGroupBackupReactions(consumer)
    }

    /**
     * Add reactions from the old ack/dec system to an existing list of emoji reactions
     */
    private fun addAckDecReactions(
        targetMessageModel: AbstractMessageModel,
        mutableEmojiReactions: MutableList<EmojiReactionData>,
    ): List<EmojiReactionData> {
        if (targetMessageModel is GroupMessageModel) {
            mutableEmojiReactions += targetMessageModel.groupMessageStates?.mapNotNull { (identity, reaction) ->
                when (reaction) {
                    MessageState.USERACK.toString() -> createEmojiReactionData(
                        targetMessageModel,
                        identity,
                        emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE,
                    )

                    MessageState.USERDEC.toString() -> createEmojiReactionData(
                        targetMessageModel,
                        identity,
                        emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE,
                    )

                    else -> null
                }
            } ?: emptyList()
        } else {
            // in case of an outgoing message only the other party can react - and vice versa
            val senderIdentity = if (targetMessageModel.isOutbox) targetMessageModel.identity!! else myIdentity
            val state = targetMessageModel.state

            when (state) {
                MessageState.USERACK -> mutableEmojiReactions += createEmojiReactionData(
                    messageModel = targetMessageModel,
                    senderIdentity = senderIdentity,
                    emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE,
                )

                MessageState.USERDEC -> mutableEmojiReactions += createEmojiReactionData(
                    messageModel = targetMessageModel,
                    senderIdentity = senderIdentity,
                    emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE,
                )

                else -> {
                    // ignore
                }
            }
        }
        return mutableEmojiReactions.toList()
    }

    private fun createEmojiReactionData(
        messageModel: AbstractMessageModel,
        senderIdentity: Identity,
        emojiSequence: String,
    ): EmojiReactionData {
        return EmojiReactionData(
            messageModel.id,
            senderIdentity,
            emojiSequence,
            messageModel.createdAt!!.toInstant(),
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
        senderIdentity: Identity,
        emojiSequence: String,
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = now(),
                )
                emojiReactionDao.create(reactionEntry, targetMessage)
                ReactionMessageIdentifier.fromMessageModel(targetMessage)
                    ?.let { reactionMessageIdentifier ->
                        cache.get(reactionMessageIdentifier)?.addEntry(reactionEntry.toDataType())
                    }
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryCreateException(exception)
            }
        }
    }

    @Throws(Exception::class)
    fun restoreContactReactions(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        emojiReactionDao.insertContactReactionsInTransaction(block)
    }

    @Throws(Exception::class)
    fun restoreGroupReactions(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        emojiReactionDao.insertGroupReactionsInTransaction(block)
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
        senderIdentity: Identity,
        emojiSequence: String,
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = now(),
                )

                emojiReactionDao.remove(reactionEntry, targetMessage)

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
                    if ((messageState == MessageState.USERACK && isThumbsUp) || (messageState == MessageState.USERDEC && isThumbsDown)) {
                        messageService.clearMessageState(targetMessage)
                    }
                }
                ReactionMessageIdentifier.fromMessageModel(targetMessage)
                    ?.let { reactionMessageIdentifier ->
                        cache.get(reactionMessageIdentifier)
                            ?.removeEntry(reactionEntry.toDataType())
                    }
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryRemoveException(exception)
            }
        }
    }

    /**
     *  We need to use this compound identifier object for emoji reactions because
     *  the [AbstractMessageModel.id] is **not** unique across different message type db tables.
     *
     *  @param messageId Represents the local auto-incremented message row-id ([AbstractMessageModel.id])
     *  @param messageType Is either [TargetMessageType.ONE_TO_ONE] or [TargetMessageType.GROUP]. Other existing
     *  message types like for example distribution-list messages can not receive emoji reactions.
     */
    data class ReactionMessageIdentifier(
        val messageId: Int,
        val messageType: TargetMessageType,
    ) {
        companion object {
            /**
             *  @return The reaction-message-identifier object or `null` if the concrete
             *  type of [AbstractMessageModel] can not receive emoji reactions.
             */
            fun fromMessageModel(messageModel: AbstractMessageModel): ReactionMessageIdentifier? {
                val messageType: TargetMessageType = when (messageModel) {
                    is MessageModel -> TargetMessageType.ONE_TO_ONE
                    is GroupMessageModel -> TargetMessageType.GROUP
                    else -> return null
                }
                return ReactionMessageIdentifier(
                    messageId = messageModel.id,
                    messageType = messageType,
                )
            }
        }

        enum class TargetMessageType { ONE_TO_ONE, GROUP }
    }
}

class EmojiReactionEntryCreateException(e: Exception) :
    ThreemaException("Failed to create emoji reaction in the db", e)

class EmojiReactionEntryRemoveException(e: Exception) :
    ThreemaException("Failed to remove emoji reaction from the db", e)
