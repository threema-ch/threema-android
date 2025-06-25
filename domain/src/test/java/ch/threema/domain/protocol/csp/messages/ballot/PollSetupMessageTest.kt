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

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testhelpers.willThrow
import com.google.protobuf.kotlin.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

open class PollSetupMessageTest {
    private val fromIdentity = "01234567"

    private val pollSetupMessage = PollSetupMessage().apply {
        ballotId = BallotId()
        ballotCreatorIdentity = "01234567"
        ballotData = BallotData().apply {
            setDescription("Cool ballot")
            setState(BallotData.State.OPEN)
            setAssessmentType(BallotData.AssessmentType.SINGLE)
            setType(BallotData.Type.INTERMEDIATE)
            setChoiceType(BallotData.ChoiceType.TEXT)
            addChoice(
                BallotDataChoiceBuilder()
                    .setId(0)
                    .setSortKey(0)
                    .setDescription("Coice 1")
                    .build(),
            )
            addChoice(
                BallotDataChoiceBuilder()
                    .setId(1)
                    .setSortKey(1)
                    .setDescription("Coice 2")
                    .build(),
            )
            addChoice(
                BallotDataChoiceBuilder()
                    .setId(2)
                    .setSortKey(2)
                    .setDescription("Coice 3")
                    .build(),
            )
            addParticipant("01234567")
            setDisplayType(BallotData.DisplayType.LIST_MODE)
        }
    }

    private val pollSetupMessageBody: ByteArray = pollSetupMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 0,
                length = 0,
                ballotCreatorIdentity = fromIdentity,
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
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = -1,
                length = 64,
                ballotCreatorIdentity = fromIdentity,
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
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 0,
                length = pollSetupMessageBody.size + 1,
                ballotCreatorIdentity = fromIdentity,
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
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 1,
                length = pollSetupMessageBody.size,
                ballotCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultPollSetupMessage = PollSetupMessage.fromByteArray(
            data = pollSetupMessageBody,
            offset = 0,
            length = pollSetupMessageBody.size,
            ballotCreatorIdentity = fromIdentity,
        )

        // assert
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollSetupMessageBody

        // act
        val resultPollSetupMessage = PollSetupMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = pollSetupMessageBody.size,
            ballotCreatorIdentity = fromIdentity,
        )

        // assert
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun fromReflectedIncomingShouldParseBodyAndSetCommonFields() {
        // act
        val incomingMessageId = 12345678L
        val incomingMessageCreatedAt: Long = System.currentTimeMillis()
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = fromIdentity
            this.messageId = incomingMessageId
            this.createdAt = incomingMessageCreatedAt
            this.body = pollSetupMessageBody.toByteString()
        }

        // act
        val resultPollSetupMessage: PollSetupMessage = PollSetupMessage.fromReflected(
            message = incomingD2DMessage,
            ballotCreatorIdentity = fromIdentity,
        )

        // assert
        assertEquals(resultPollSetupMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultPollSetupMessage.date.time, incomingMessageCreatedAt)
        assertEquals(resultPollSetupMessage.fromIdentity, fromIdentity)
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {
        // act
        val outgoingMessageId = MessageId.random()
        val outgoingMessageCreatedAt: Long = 42424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = pollSetupMessageBody.toByteString()
        }

        // act
        val resultPollSetupMessage: PollSetupMessage = PollSetupMessage.fromReflected(
            message = outgoingD2DMessage,
            ballotCreatorIdentity = fromIdentity,
        )

        // assert
        assertEquals(resultPollSetupMessage.messageId, outgoingMessageId)
        assertEquals(resultPollSetupMessage.date.time, outgoingMessageCreatedAt)
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollSetupMessageBody

        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = pollSetupMessageBody.size,
                ballotCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    private fun assertPollSetupMessageFields(actual: PollSetupMessage?) {
        if (actual?.ballotData == null) {
            fail()
        }
        assertEquals(pollSetupMessage.ballotId, actual.ballotId)
        assertEquals(pollSetupMessage.ballotCreatorIdentity, actual.ballotCreatorIdentity)
        assertEquals(pollSetupMessage.ballotData!!.description, actual.ballotData!!.description)
        assertEquals(pollSetupMessage.ballotData!!.state, actual.ballotData!!.state)
        assertEquals(
            pollSetupMessage.ballotData!!.assessmentType,
            actual.ballotData!!.assessmentType,
        )
        assertEquals(pollSetupMessage.ballotData!!.type, actual.ballotData!!.type)
        assertEquals(pollSetupMessage.ballotData!!.choiceType, actual.ballotData!!.choiceType)
        assertContentEquals(
            pollSetupMessage.ballotData!!.participants,
            actual.ballotData!!.participants,
        )
        assertEquals(pollSetupMessage.ballotData!!.displayType, actual.ballotData!!.displayType)
        assertEquals(
            pollSetupMessage.ballotData!!.choiceList.size,
            actual.ballotData!!.choiceList.size,
        )
        pollSetupMessage.ballotData!!.choiceList.forEachIndexed { index, value ->
            assertEquals(value.id, actual.ballotData!!.choiceList[index].id)
            assertEquals(value.name, actual.ballotData!!.choiceList[index].name)
            assertEquals(value.order, actual.ballotData!!.choiceList[index].order)
            assertContentEquals(
                value.ballotDataChoiceResults,
                actual.ballotData!!.choiceList[index].ballotDataChoiceResults,
            )
            assertEquals(value.totalVotes, actual.ballotData!!.choiceList[index].totalVotes)
        }
    }
}
