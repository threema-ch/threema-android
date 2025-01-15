/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

open class PollVoteMessageTest {

    private val pollVoteMessage = PollVoteMessage().apply {
        ballotId = BallotId()
        ballotCreatorIdentity = "01234567"
        addVotes(
            listOf(
                BallotVote(2, 8),
                BallotVote(3, 5),
                BallotVote(4, 1)
            )
        )
    }

    private val pollVoteMessageBody: ByteArray = pollVoteMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {

            // act
            PollVoteMessage.fromByteArray(
                data = pollVoteMessageBody,
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
            PollVoteMessage.fromByteArray(
                data = pollVoteMessageBody,
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
            PollVoteMessage.fromByteArray(
                data = pollVoteMessageBody,
                offset = 0,
                length = pollVoteMessageBody.size + 1
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
            PollVoteMessage.fromByteArray(
                data = pollVoteMessageBody,
                offset = 1,
                length = pollVoteMessageBody.size
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {

        // act
        val resultPollVoteMessage = PollVoteMessage.fromByteArray(
            data = pollVoteMessageBody,
            offset = 0,
            length = pollVoteMessageBody.size
        )

        // assert
        assertPollVoteMessageContainsCorrectValues(resultPollVoteMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {

        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollVoteMessageBody

        // act
        val resultPollVoteMessage: PollVoteMessage = PollVoteMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = pollVoteMessageBody.size
        )

        // assert
        assertPollVoteMessageContainsCorrectValues(resultPollVoteMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {

        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollVoteMessageBody

        val testBlockLazy = {

            // act
            PollVoteMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = pollVoteMessageBody.size
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
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
            this.body = pollVoteMessageBody.toByteString()
        }

        // act
        val resultPollVoteMessage: PollVoteMessage = PollVoteMessage.fromReflected(incomingD2DMessage)

        // assert
        assertEquals(resultPollVoteMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultPollVoteMessage.date.time, incomingMessageCreatedAt)
        assertEquals(resultPollVoteMessage.fromIdentity, incomingMessageSenderIdentity)
        assertPollVoteMessageContainsCorrectValues(resultPollVoteMessage)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {

        // act
        val outgoingMessageId = MessageId()
        val outgoingMessageCreatedAt: Long = System.currentTimeMillis()
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = pollVoteMessageBody.toByteString()
        }

        // act
        val resultPollVoteMessage: PollVoteMessage = PollVoteMessage.fromReflected(outgoingD2DMessage)

        // assert
        assertEquals(outgoingMessageId, resultPollVoteMessage.messageId)
        assertEquals(outgoingMessageCreatedAt, resultPollVoteMessage.date.time)
        assertPollVoteMessageContainsCorrectValues(resultPollVoteMessage)
    }

    private fun assertPollVoteMessageContainsCorrectValues(actual: PollVoteMessage?) {
        if (actual == null) {
            fail()
        }
        assertEquals(pollVoteMessage.ballotId, actual.ballotId)
        assertEquals(pollVoteMessage.ballotCreatorIdentity, actual.ballotCreatorIdentity)
        assertEquals(pollVoteMessage.votes.size, actual.votes.size)
        pollVoteMessage.votes.forEachIndexed { index, vote ->
            assertEquals(vote.id, actual.votes[index].id)
            assertEquals(vote.value, actual.votes[index].value)
        }
    }
}
