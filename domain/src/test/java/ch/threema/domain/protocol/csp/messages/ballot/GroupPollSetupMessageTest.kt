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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

open class GroupPollSetupMessageTest {
    private val fromIdentity = "01234567"

    private val groupPollSetupMessage = GroupPollSetupMessage().apply {

        ballotId = BallotId()
        groupCreator = "01234567"
        apiGroupId = GroupId()
        ballotCreatorIdentity = "01234567"

        ballotData = BallotData().apply {
            this.setDescription("Cool ballot")
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
            addParticipant("0Y123456")
            setDisplayType(BallotData.DisplayType.LIST_MODE)
        }
    }

    private val groupPollSetupMessageBody: ByteArray = groupPollSetupMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 0,
                length = 0,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = -1,
                length = 64,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 0,
                length = groupPollSetupMessageBody.size + 1,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 1,
                length = groupPollSetupMessageBody.size,
                fromIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultGroupPollSetupMessage = GroupPollSetupMessage.fromByteArray(
            data = groupPollSetupMessageBody,
            offset = 0,
            length = groupPollSetupMessageBody.size,
            fromIdentity = fromIdentity,
        )

        // assert
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollSetupMessageBody

        // act
        val resultGroupPollSetupMessage = GroupPollSetupMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = groupPollSetupMessageBody.size,
            fromIdentity = fromIdentity,
        )

        // assert
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
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
            this.body = groupPollSetupMessageBody.toByteString()
        }

        // act
        val resultGroupPollSetupMessage: GroupPollSetupMessage =
            GroupPollSetupMessage.fromReflected(
                message = incomingD2DMessage,
                fromIdentity = fromIdentity,
            )

        // assert
        assertEquals(resultGroupPollSetupMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultGroupPollSetupMessage.date.time, incomingMessageCreatedAt)
        assertEquals(resultGroupPollSetupMessage.fromIdentity, fromIdentity)
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {
        // act
        val outgoingMessageId = MessageId()
        val outgoingMessageCreatedAt: Long = 42424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = groupPollSetupMessageBody.toByteString()
        }

        // act
        val resultGroupPollSetupMessage: GroupPollSetupMessage =
            GroupPollSetupMessage.fromReflected(
                message = outgoingD2DMessage,
                fromIdentity = fromIdentity,
            )

        // assert
        assertEquals(outgoingMessageId, resultGroupPollSetupMessage.messageId)
        assertEquals(outgoingMessageCreatedAt, resultGroupPollSetupMessage.date.time)
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollSetupMessageBody

        val testBlockLazy = {
            // act
            GroupPollSetupMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = groupPollSetupMessageBody.size,
                fromIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    private fun assertGroupPollSetupMessageFields(actual: GroupPollSetupMessage?) {
        if (actual?.ballotData == null) {
            fail()
        }
        assertEquals(groupPollSetupMessage.ballotId, actual.ballotId)
        assertEquals(groupPollSetupMessage.groupCreator, actual.groupCreator)
        assertEquals(groupPollSetupMessage.apiGroupId, actual.apiGroupId)
        assertEquals(groupPollSetupMessage.ballotCreatorIdentity, actual.ballotCreatorIdentity)
        assertEquals(
            groupPollSetupMessage.ballotData!!.description,
            actual.ballotData!!.description,
        )
        assertEquals(groupPollSetupMessage.ballotData!!.state, actual.ballotData!!.state)
        assertEquals(
            groupPollSetupMessage.ballotData!!.assessmentType,
            actual.ballotData!!.assessmentType,
        )
        assertEquals(groupPollSetupMessage.ballotData!!.type, actual.ballotData!!.type)
        assertEquals(groupPollSetupMessage.ballotData!!.choiceType, actual.ballotData!!.choiceType)
        assertContentEquals(
            groupPollSetupMessage.ballotData!!.participants,
            actual.ballotData!!.participants,
        )
        assertEquals(
            groupPollSetupMessage.ballotData!!.displayType,
            actual.ballotData!!.displayType,
        )
        assertEquals(
            groupPollSetupMessage.ballotData!!.choiceList.size,
            actual.ballotData!!.choiceList.size,
        )
        groupPollSetupMessage.ballotData!!.choiceList.forEachIndexed { index, value ->
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
