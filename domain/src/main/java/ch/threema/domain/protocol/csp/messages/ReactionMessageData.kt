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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.Reaction
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Objects

class ReactionMessageData(
    val messageId: Long,
    val actionCase: ActionCase,
    val emojiSequenceBytes: ByteString?
) : ProtobufDataInterface<Reaction> {

    companion object {
        @JvmStatic
        fun fromProtobuf(rawProtobufMessage: ByteArray): ReactionMessageData {
            try {
                val protobufMessage = Reaction.parseFrom(rawProtobufMessage)
                return ReactionMessageData(
                    protobufMessage.messageId,
                    protobufMessage.actionCase,
                    when (protobufMessage.actionCase) {
                        ActionCase.APPLY -> protobufMessage.apply
                        ActionCase.WITHDRAW -> protobufMessage.withdraw
                        ActionCase.ACTION_NOT_SET -> null
                        else -> throw BadMessageException("Unrecognized action case")
                    }
                )
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid Reaction protobuf data")
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create Reaction data", e)
            }
        }
    }

    override fun toProtobufMessage(): Reaction {
        val builder = Reaction.newBuilder()
            .setMessageId(messageId)

        when (actionCase) {
            ActionCase.APPLY -> builder.apply = emojiSequenceBytes
            ActionCase.WITHDRAW -> builder.withdraw = emojiSequenceBytes
            ActionCase.ACTION_NOT_SET -> throw BadMessageException("Missing action case in reaction message")
        }

        return builder.build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReactionMessageData) return false

        if (messageId != other.messageId) return false
        if (actionCase != other.actionCase) return false
        if (emojiSequenceBytes != other.emojiSequenceBytes) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(messageId, actionCase, emojiSequenceBytes)
    }
}
