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

package ch.threema.data.models

import ch.threema.data.storage.DbEmojiReaction
import java.util.Date

data class EmojiReactionData(
    /** The id of the message this reaction refers to - see [ch.threema.storage.models.AbstractMessageModel.COLUMN_ID] */
    @JvmField val messageId: Int,
    /** The identity of the person who reacted. This may differ from the sender of the message **/
    @JvmField val senderIdentity: String,
    /** The emoji codepoint sequence of the reaction. This can never be empty */
    @JvmField val emojiSequence: String,
    /** Timestamp when the reaction was locally created. */
    @JvmField val reactedAt: Date
) {
    fun messageId() = messageId

    fun senderIdentity() = senderIdentity

    fun emojiSequence() = emojiSequence

    fun reactedAt() = reactedAt

    override fun equals(other: Any?): Boolean {
        if (other !is EmojiReactionData) return false

        return this.messageId == other.messageId &&
            this.senderIdentity == other.senderIdentity &&
            this.emojiSequence == other.emojiSequence
    }

    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + senderIdentity.hashCode()
        result = 31 * result + emojiSequence.hashCode()
        return result
    }

}

fun DbEmojiReaction.toDataType() = EmojiReactionData(
    messageId = this.messageId,
    senderIdentity = this.senderIdentity,
    emojiSequence = this.emojiSequence,
    reactedAt = this.reactedAt
)
