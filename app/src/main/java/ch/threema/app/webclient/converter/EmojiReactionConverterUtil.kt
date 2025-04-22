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

package ch.threema.app.webclient.converter

import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.data.models.EmojiReactionData
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import java.util.Optional
import kotlin.jvm.Throws

// TODO(ANDR-3517): Remove
object EmojiReactionConverterUtil {
    @Throws(ConversionException::class)
    @JvmStatic
    fun getContactAckDecFromReactions(
        message: MessageModel,
        reactions: List<EmojiReactionData>,
    ): Optional<MessageState> {
        val ackDecReactions = getGroupAckDecFromReactions(reactions)
            .filter {
                if (message.isOutbox) {
                    it.key == message.identity
                } else {
                    it.key != message.identity
                }
            }

        return when {
            ackDecReactions.isEmpty() -> Optional.empty()
            ackDecReactions.size == 1 -> Optional.of(ackDecReactions.values.first())
            else -> throw ConversionException("Invalid number of ack/dec reactions (${ackDecReactions.size})")
        }
    }

    @Throws(ConversionException::class)
    @JvmStatic
    fun getGroupAckDecFromReactions(emojiReactions: List<EmojiReactionData>): Map<String, MessageState> {
        return emojiReactions
            .filter { EmojiUtil.isThumbsUpEmoji(it.emojiSequence) || EmojiUtil.isThumbsDownEmoji(it.emojiSequence()) }
            .groupBy { it.senderIdentity }
            .mapValues { (_, reactions) -> reactions.maxBy { reaction -> reaction.reactedAt } }
            .mapValues { (_, reaction) ->
                when {
                    EmojiUtil.isThumbsUpEmoji(reaction.emojiSequence) -> MessageState.USERACK
                    EmojiUtil.isThumbsDownEmoji(reaction.emojiSequence) -> MessageState.USERDEC
                    else -> throw ConversionException("Reaction cannot be mapped to ack/dec")
                }
            }
    }
}
