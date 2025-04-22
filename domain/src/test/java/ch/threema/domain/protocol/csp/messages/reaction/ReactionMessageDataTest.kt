/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.reaction

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.protobuf.csp.e2e.Reaction
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import ch.threema.protobuf.csp.e2e.reaction
import ch.threema.testutils.willThrow
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReactionMessageDataTest {
    private val testMessageId = MessageId()
    private val dragonEmoji: String = Character.toString(128_009) // 🐉

    @Test
    fun fromProtobufShouldParseApply() {
        // arrange
        val reactionMessage = reaction {
            this.messageId = testMessageId.messageIdLong
            this.apply = dragonEmoji.toByteStringUtf8()
        }

        // act
        val reactionMessageData = ReactionMessageData.fromProtobuf(reactionMessage.toByteArray())

        // assert
        assertEquals(
            ReactionMessageData.Apply(
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            ),
            reactionMessageData,
        )
    }

    @Test
    fun fromProtobufShouldParseWithdraw() {
        // arrange
        val reactionMessage = reaction {
            this.messageId = testMessageId.messageIdLong
            this.withdraw = dragonEmoji.toByteStringUtf8()
        }

        // act
        val reactionMessageData = ReactionMessageData.fromProtobuf(reactionMessage.toByteArray())

        // assert
        assertEquals(
            ReactionMessageData.Withdraw(
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            ),
            reactionMessageData,
        )
    }

    @Test
    fun fromProtobufShouldFailWhenMissingApplyBytes() {
        // arrange
        val reactionMessage = reaction {
            this.messageId = testMessageId.messageIdLong
            this.apply = ByteString.empty()
        }

        // act
        val codeUnderTest = {
            ReactionMessageData.fromProtobuf(reactionMessage.toByteArray())
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun fromProtobufShouldFailWhenMissingWithdrawBytes() {
        // arrange
        val reactionMessage = reaction {
            this.messageId = testMessageId.messageIdLong
            this.withdraw = ByteString.empty()
        }

        // act
        val codeUnderTest = {
            ReactionMessageData.fromProtobuf(reactionMessage.toByteArray())
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun fromProtobufShouldFailWhenActionCaseNotSet() {
        // arrange
        val reactionMessage = reaction {
            this.messageId = testMessageId.messageIdLong
        }

        // act
        val codeUnderTest = {
            ReactionMessageData.fromProtobuf(reactionMessage.toByteArray())
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun fromProtobufShouldFailForInvalidProtoBytes() {
        // arrange
        val reactionMessageBytes = reaction {
            this.messageId = testMessageId.messageIdLong
            this.withdraw = dragonEmoji.toByteStringUtf8()
        }.toByteArray()
        val cutOffReactionMessageBytes = reactionMessageBytes.copyOfRange(
            0,
            reactionMessageBytes.size / 2,
        )

        // act
        val codeUnderTest = {
            ReactionMessageData.fromProtobuf(cutOffReactionMessageBytes)
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun toProtobufSetsValuesForApply() {
        // arrange
        val reactionMessageData = ReactionMessageData.Apply(
            messageId = testMessageId.messageIdLong,
            emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
        )

        // act
        val reactionProto: Reaction = reactionMessageData.toProtobufMessage()

        // assert
        assertEquals(testMessageId.messageIdLong, reactionProto.messageId)
        assertEquals(dragonEmoji.toByteStringUtf8(), reactionProto.apply)
        assertEquals(ActionCase.APPLY, reactionProto.actionCase)
        assertFalse(reactionProto.hasWithdraw())
    }

    @Test
    fun toProtobufSetsValuesForWithdraw() {
        // arrange
        val reactionMessageData = ReactionMessageData.Withdraw(
            messageId = testMessageId.messageIdLong,
            emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
        )

        // act
        val reactionProto: Reaction = reactionMessageData.toProtobufMessage()

        // assert
        assertEquals(testMessageId.messageIdLong, reactionProto.messageId)
        assertEquals(dragonEmoji.toByteStringUtf8(), reactionProto.withdraw)
        assertEquals(ActionCase.WITHDRAW, reactionProto.actionCase)
        assertFalse(reactionProto.hasApply())
    }

    @Test
    fun toProtobufFailsWhenMissingEmojiSequence() {
        // act
        val testBlocks = listOf(
            {
                ReactionMessageData.Apply(testMessageId.messageIdLong, ByteString.empty())
                    .toProtobufMessage()
            },
            {
                ReactionMessageData.Withdraw(testMessageId.messageIdLong, ByteString.empty())
                    .toProtobufMessage()
            },
        )

        // assert
        testBlocks.forEach { codeUnderTest ->
            codeUnderTest willThrow BadMessageException::class
        }
    }

    @Test
    fun forActionCaseShouldCreateApply() {
        // act
        val reactionMessageData = ReactionMessageData.forActionCase(
            actionCase = ActionCase.APPLY,
            messageId = testMessageId.messageIdLong,
            emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
        )

        // assert
        assertEquals(
            ReactionMessageData.Apply(
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            ),
            reactionMessageData,
        )
    }

    @Test
    fun forActionCaseShouldCreateWithdraw() {
        // act
        val reactionMessageData = ReactionMessageData.forActionCase(
            actionCase = ActionCase.WITHDRAW,
            messageId = testMessageId.messageIdLong,
            emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
        )

        // assert
        assertEquals(
            ReactionMessageData.Withdraw(
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            ),
            reactionMessageData,
        )
    }

    @Test
    fun forActionCaseShouldFailForActionCaseNotSet() {
        // act
        val codeUnderTest = {
            ReactionMessageData.forActionCase(
                actionCase = ActionCase.ACTION_NOT_SET,
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun forActionCaseShouldFailForEmptyEmojiSequence() {
        // act
        val testBlocks = listOf(
            {
                ReactionMessageData.forActionCase(
                    ActionCase.APPLY,
                    testMessageId.messageIdLong,
                    ByteString.empty(),
                )
            },
            {
                ReactionMessageData.forActionCase(
                    ActionCase.WITHDRAW,
                    testMessageId.messageIdLong,
                    ByteString.empty(),
                )
            },
            {
                ReactionMessageData.forActionCase(
                    ActionCase.ACTION_NOT_SET,
                    testMessageId.messageIdLong,
                    ByteString.empty(),
                )
            },
        )

        // assert
        testBlocks.forEach { codeUnderTest ->
            codeUnderTest willThrow BadMessageException::class
        }
    }
}
