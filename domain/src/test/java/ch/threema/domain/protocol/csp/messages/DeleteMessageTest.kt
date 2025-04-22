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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import kotlin.random.Random.Default.nextBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteMessageTest {
    /**
     *  A delete message in the raw form consists of bytes in the following order:
     *
     *  (message id bytes (8))
     */
    private val messageId: MessageId = MessageId()
    private val protobufMessageId: ByteArray =
        DeleteMessageData(messageId.messageIdLong).toProtobufBytes()

    private val bytesMessageData: ByteArray = protobufMessageId

    @Test
    fun shouldThrowBadMessageWhenLengthIsTooLow() {
        // arrange
        val testBlockLazy = {
            // act
            DeleteMessage.fromByteArray(
                data = bytesMessageData,
                offset = 0,
                length = ProtocolDefines.MESSAGE_ID_LEN - 1,
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
            DeleteMessage.fromByteArray(
                data = bytesMessageData,
                offset = -1,
                length = bytesMessageData.size,
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
            DeleteMessage.fromByteArray(
                data = bytesMessageData,
                offset = 1,
                length = bytesMessageData.size,
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
            DeleteMessage.fromByteArray(
                data = bytesMessageData.copyOfRange(0, bytesMessageData.size - 1),
                offset = 0,
                length = bytesMessageData.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldParseValuesCorrectly() {
        // act
        val deleteMessage = DeleteMessage.fromByteArray(
            data = bytesMessageData,
            offset = 0,
            length = bytesMessageData.size,
        )

        // assert
        assertEquals(messageId.messageIdLong, deleteMessage.data.messageId)
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffset() {
        // arrange
        val offset = 5
        val bytesMessageDataContainingOffset = nextBytes(offset) + bytesMessageData

        // act
        val deleteMessage = DeleteMessage.fromByteArray(
            data = bytesMessageDataContainingOffset,
            offset = offset,
            length = bytesMessageData.size,
        )

        // assert
        assertEquals(messageId.messageIdLong, deleteMessage.data.messageId)
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffsetAndTooMuchData() {
        // arrange
        val offset = 5
        val bytesMessageDataContainingOffsetAndJunk =
            nextBytes(offset) + bytesMessageData + nextBytes(10)

        // act
        val deleteMessage = DeleteMessage.fromByteArray(
            data = bytesMessageDataContainingOffsetAndJunk,
            offset = offset,
            length = bytesMessageData.size,
        )

        // assert
        assertEquals(messageId.messageIdLong, deleteMessage.data.messageId)
    }

    @Test
    fun fromReflectedIncomingShouldAlsoSetDefaultMessageValues() {
        // arrange
        val incomingMessageId = 12345678L
        val incomingMessageCreatedAt: Long = System.currentTimeMillis()
        val incomingMessageSenderIdentity = "01234567"
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = incomingMessageSenderIdentity
            this.messageId = incomingMessageId
            this.createdAt = incomingMessageCreatedAt
            this.body = bytesMessageData.toByteString()
        }

        // act
        val deleteMessage = DeleteMessage.fromReflected(incomingD2DMessage)

        // assert
        assertEquals(incomingMessageId, deleteMessage.messageId.messageIdLong)
        assertEquals(incomingMessageCreatedAt, deleteMessage.date.time)
        assertEquals(incomingMessageSenderIdentity, deleteMessage.fromIdentity)
        assertEquals(messageId.messageIdLong, deleteMessage.data.messageId)
    }

    @Test
    fun fromReflectedOutgoingShouldAlsoSetDefaultMessageValues() {
        // arrange
        val outgoingMessageId = 12345678L
        val outgoingMessageCreatedAt: Long = System.currentTimeMillis()
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId
            this.createdAt = outgoingMessageCreatedAt
            this.body = bytesMessageData.toByteString()
        }

        // act
        val deleteMessage = DeleteMessage.fromReflected(outgoingD2DMessage)

        // assert
        assertEquals(outgoingMessageId, deleteMessage.messageId.messageIdLong)
        assertEquals(outgoingMessageCreatedAt, deleteMessage.date.time)
        assertEquals(messageId.messageIdLong, deleteMessage.data.messageId)
    }
}
