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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.Reaction
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import ch.threema.protobuf.csp.e2e.reaction
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.kotlin.isNotEmpty

sealed class ReactionMessageData(
    open val messageId: Long,
    open val emojiSequenceBytes: ByteString,
) : ProtobufDataInterface<Reaction> {
    abstract val actionCase: ActionCase

    data class Apply(
        override val messageId: Long,
        override val emojiSequenceBytes: ByteString,
    ) : ReactionMessageData(messageId, emojiSequenceBytes) {
        override val actionCase: ActionCase = ActionCase.APPLY

        override fun toProtobufMessage(): Reaction = reaction {
            messageId = this@Apply.messageId
            apply = this@Apply.emojiSequenceBytes.also { byteString ->
                if (byteString.isEmpty) throw BadMessageException("Missing emoji sequence for action case ${ActionCase.APPLY}")
            }
        }

        override fun toString(): String =
            "Apply(messageId:$messageId, emojiSequence:${emojiSequenceBytes.toStringUtf8()})"
    }

    data class Withdraw(
        override val messageId: Long,
        override val emojiSequenceBytes: ByteString,
    ) : ReactionMessageData(messageId, emojiSequenceBytes) {
        override val actionCase: ActionCase = ActionCase.WITHDRAW

        override fun toProtobufMessage(): Reaction = reaction {
            messageId = this@Withdraw.messageId
            withdraw = this@Withdraw.emojiSequenceBytes.also { byteString ->
                if (byteString.isEmpty) throw BadMessageException("Missing emoji sequence for action case ${ActionCase.WITHDRAW}")
            }
        }

        override fun toString(): String =
            "Withdraw(messageId:$messageId, emojiSequence:${emojiSequenceBytes.toStringUtf8()})"
    }

    companion object {
        /**
         * @throws BadMessageException If the `actionCase` is not `APPLY` or `WITHDRAW`, or if the emoji sequence byte string is empty.
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromProtobuf(rawProtobufMessage: ByteArray): ReactionMessageData {
            val reactionMessage = try {
                Reaction.parseFrom(rawProtobufMessage)
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid Reaction protobuf data", e)
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create Reaction data", e)
            }

            return when (reactionMessage.actionCase) {
                ActionCase.APPLY -> {
                    val emojiSequenceApply = reactionMessage.apply.takeIf(ByteString::isNotEmpty)
                        ?: throw BadMessageException("Missing emoji sequence for action case ${reactionMessage.actionCase}")
                    Apply(
                        messageId = reactionMessage.messageId,
                        emojiSequenceBytes = emojiSequenceApply,
                    )
                }

                ActionCase.WITHDRAW -> {
                    val emojiSequenceWithdraw =
                        reactionMessage.withdraw.takeIf(ByteString::isNotEmpty)
                            ?: throw BadMessageException("Missing emoji sequence for action case ${reactionMessage.actionCase}")
                    Withdraw(
                        messageId = reactionMessage.messageId,
                        emojiSequenceBytes = emojiSequenceWithdraw,
                    )
                }

                else -> throw BadMessageException(
                    "ActionCase must either by ${ActionCase.APPLY} or ${ActionCase.WITHDRAW}, but was ${reactionMessage.actionCase}",
                )
            }
        }

        /**
         * @return The specific `ReactionMessageData` implementation for the given [actionCase].
         * @throws BadMessageException If [emojiSequenceBytes] is empty or if [actionCase] is [ActionCase.ACTION_NOT_SET].
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun forActionCase(
            actionCase: ActionCase,
            messageId: Long,
            emojiSequenceBytes: ByteString,
        ): ReactionMessageData {
            if (emojiSequenceBytes.isEmpty) {
                throw BadMessageException("Missing emoji sequence")
            }
            return when (actionCase) {
                ActionCase.APPLY -> Apply(messageId, emojiSequenceBytes)
                ActionCase.WITHDRAW -> Withdraw(messageId, emojiSequenceBytes)
                ActionCase.ACTION_NOT_SET -> throw BadMessageException("Can not create message data for action case $actionCase")
            }
        }
    }
}
