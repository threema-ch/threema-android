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

package ch.threema.domain.protocol.csp.messages.ballot

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

open class GroupPollVoteMessageTest {
    private val groupPollVoteMessage = GroupPollVoteMessage().apply {

        groupCreator = "01234567"
        apiGroupId = GroupId()
        ballotCreatorIdentity = "01234567"
        ballotId = BallotId()

        addVotes(
            listOf(
                BallotVote(2, 8),
                BallotVote(3, 5),
                BallotVote(4, 1),
            ),
        )
    }

    private var groupPollVoteMessageBody: ByteArray = groupPollVoteMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollVoteMessage.fromByteArray(
                data = groupPollVoteMessageBody,
                offset = 0,
                length = 0,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetBelowZero() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollVoteMessage.fromByteArray(
                data = groupPollVoteMessageBody,
                offset = -1,
                length = 64,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLength() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollVoteMessage.fromByteArray(
                data = groupPollVoteMessageBody,
                offset = 0,
                length = groupPollVoteMessageBody.size + 1,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLengthWithOffset() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollVoteMessage.fromByteArray(
                data = groupPollVoteMessageBody,
                offset = 1,
                length = groupPollVoteMessageBody.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultGroupPollVoteMessage = GroupPollVoteMessage.fromByteArray(
            data = groupPollVoteMessageBody,
            offset = 0,
            length = groupPollVoteMessageBody.size,
        )

        // assert
        assertGroupPollVoteMessageFields(resultGroupPollVoteMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollVoteMessageBody

        // act
        val resultGroupPollVoteMessage: GroupPollVoteMessage = GroupPollVoteMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = groupPollVoteMessageBody.size,
        )

        // assert
        assertGroupPollVoteMessageFields(resultGroupPollVoteMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollVoteMessageBody

        val testBlockLazy = {
            // act
            GroupPollVoteMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = groupPollVoteMessageBody.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {
        // act
        val outgoingMessageId = MessageId()
        val outgoingMessageCreatedAt: Long = 42424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = groupPollVoteMessageBody.toByteString()
        }

        // act
        val resultGroupPollVoteMessage: GroupPollVoteMessage =
            GroupPollVoteMessage.fromReflected(outgoingD2DMessage)

        // assert
        assertEquals(outgoingMessageId, resultGroupPollVoteMessage.messageId)
        assertEquals(outgoingMessageCreatedAt, resultGroupPollVoteMessage.date.time)
        assertGroupPollVoteMessageFields(resultGroupPollVoteMessage)
    }

    @Test
    fun fromReflectedIncomingShouldParseBodyAndSetCommonFields() {
        // act
        val incomingMessageId = 12345678L
        val incomingMessageCreatedAt: Long = System.currentTimeMillis()
        val incomingMessageSenderIdentity = "01234567"
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = incomingMessageSenderIdentity
            this.messageId = incomingMessageId
            this.createdAt = incomingMessageCreatedAt
            this.body = groupPollVoteMessageBody.toByteString()
        }

        // act
        val resultGroupPollVoteMessage: GroupPollVoteMessage =
            GroupPollVoteMessage.fromReflected(incomingD2DMessage)

        // assert
        assertEquals(resultGroupPollVoteMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultGroupPollVoteMessage.date.time, incomingMessageCreatedAt)
        assertEquals(resultGroupPollVoteMessage.fromIdentity, incomingMessageSenderIdentity)
        assertGroupPollVoteMessageFields(resultGroupPollVoteMessage)
    }

    private fun assertGroupPollVoteMessageFields(actual: GroupPollVoteMessage?) {
        if (actual == null) {
            fail()
        }
        assertEquals(groupPollVoteMessage.groupCreator, actual.groupCreator)
        assertEquals(groupPollVoteMessage.apiGroupId, actual.apiGroupId)
        assertEquals(groupPollVoteMessage.ballotCreatorIdentity, actual.ballotCreatorIdentity)
        assertEquals(groupPollVoteMessage.ballotId, actual.ballotId)
        assertEquals(groupPollVoteMessage.votes.size, actual.votes.size)
        groupPollVoteMessage.votes.forEachIndexed { index, vote ->
            assertEquals(vote.id, actual.votes[index].id)
            assertEquals(vote.value, actual.votes[index].value)
        }
    }
}
