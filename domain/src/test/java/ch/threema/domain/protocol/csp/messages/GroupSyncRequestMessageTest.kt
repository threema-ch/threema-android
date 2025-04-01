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

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.protobuf.d2d.conversationId
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.protobuf.groupIdentity
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import org.junit.Test
import kotlin.random.Random.Default.nextBytes
import kotlin.test.assertEquals

class GroupSyncRequestMessageTest {

    private val creatorIdentity = "12345678"
    private val groupId = GroupId(42)
    private val validMessageBytes = GroupSyncRequestMessage().apply {
        groupCreator = creatorIdentity
        apiGroupId = groupId
    }.body!!

    @Test
    fun shouldThrowBadMessageWhenLengthIsTooLow() {

        // arrange
        val testBlockLazy = {

            // act
            GroupSyncRequestMessage.fromByteArray(
                data = validMessageBytes,
                offset = 0,
                length = validMessageBytes.size - 1,
                creatorIdentity = creatorIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageWhenOffsetIsBelowZero() {

        // arrange
        val testBlockLazy = {

            // act
            GroupSyncRequestMessage.fromByteArray(
                data = validMessageBytes,
                offset = -1,
                length = validMessageBytes.size,
                creatorIdentity = creatorIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageWhenDataIsSmallerThanLengthAndOffset1() {

        // arrange
        val testBlockLazy = {

            // act
            GroupSyncRequestMessage.fromByteArray(
                data = validMessageBytes,
                offset = 1,
                length = validMessageBytes.size,
                creatorIdentity = creatorIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageWhenDataIsSmallerThanLengthAndOffset2() {

        // arrange
        val testBlockLazy = {

            // act
            GroupSyncRequestMessage.fromByteArray(
                data = validMessageBytes.dropLast(1).toByteArray(),
                offset = 0,
                length = validMessageBytes.size,
                creatorIdentity = creatorIdentity
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldParseValuesCorrectly() {

        // act
        val groupSyncRequestMessage = GroupSyncRequestMessage.fromByteArray(
            data = validMessageBytes,
            offset = 0,
            length = validMessageBytes.size,
            creatorIdentity = creatorIdentity,
        )

        // assert
        assertEquals(creatorIdentity, groupSyncRequestMessage.groupCreator)
        assertEquals(groupId.toLong(), groupSyncRequestMessage.apiGroupId.toLong())
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffset() {

        // arrange
        val offset = 5
        val bytesMessageDataContainingOffset = nextBytes(offset) + validMessageBytes

        // act
        val groupSyncRequestMessage = GroupSyncRequestMessage.fromByteArray(
            data = bytesMessageDataContainingOffset,
            offset = offset,
            length = validMessageBytes.size,
            creatorIdentity = creatorIdentity,
        )

        // assert
        assertEquals(creatorIdentity, groupSyncRequestMessage.groupCreator)
        assertEquals(groupId.toLong(), groupSyncRequestMessage.apiGroupId.toLong())
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffsetAndTooMuchData() {

        // arrange
        val offset = 5
        val bytesMessageDataContainingOffsetAndJunk =
            nextBytes(offset) + validMessageBytes + nextBytes(10)

        // act
        val groupSyncRequestMessage = GroupSyncRequestMessage.fromByteArray(
            data = bytesMessageDataContainingOffsetAndJunk,
            offset = offset,
            length = validMessageBytes.size,
            creatorIdentity = creatorIdentity,
        )

        // assert
        assertEquals(creatorIdentity, groupSyncRequestMessage.groupCreator)
        assertEquals(groupId.toLong(), groupSyncRequestMessage.apiGroupId.toLong())
    }

    @Test
    fun fromReflectedIncomingShouldAlsoSetDefaultMessageValues() {

        // arrange
        val incomingMessageId = MessageId(42)
        val incomingMessageCreatedAt: Long = 42424242
        val incomingMessageSenderIdentity = "TESTTEST"
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = incomingMessageSenderIdentity
            this.messageId = incomingMessageId.messageIdLong
            this.createdAt = incomingMessageCreatedAt
            this.body = validMessageBytes.toByteString()
        }

        // act
        val groupSyncRequestMessage =
            GroupSyncRequestMessage.fromReflected(incomingD2DMessage, creatorIdentity)

        // assert
        assertEquals(incomingMessageId, groupSyncRequestMessage.messageId)
        assertEquals(incomingMessageCreatedAt, groupSyncRequestMessage.date.time)
        assertEquals(incomingMessageSenderIdentity, groupSyncRequestMessage.fromIdentity)

        assertEquals(creatorIdentity, groupSyncRequestMessage.groupCreator)
        assertEquals(groupId.toLong(), groupSyncRequestMessage.apiGroupId.toLong())
    }

    @Test
    fun fromReflectedOutgoingShouldAlsoSetDefaultMessageValues() {

        // arrange
        val outgoingMessageId = MessageId(42)
        val outgoingMessageCreatedAt: Long = 424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = validMessageBytes.toByteString()
            this.conversation = conversationId {
                this.group = groupIdentity {
                    this.creatorIdentity = this@GroupSyncRequestMessageTest.creatorIdentity
                    this.groupId = this@GroupSyncRequestMessageTest.groupId.toLong()
                }
            }
        }

        // act
        val groupSyncRequestMessage = GroupSyncRequestMessage.fromReflected(outgoingD2DMessage)

        // assert
        assertEquals(outgoingMessageId, groupSyncRequestMessage.messageId)
        assertEquals(outgoingMessageCreatedAt, groupSyncRequestMessage.date.time)

        assertEquals(creatorIdentity, groupSyncRequestMessage.groupCreator)
        assertEquals(groupId.toLong(), groupSyncRequestMessage.apiGroupId.toLong())
    }
}
