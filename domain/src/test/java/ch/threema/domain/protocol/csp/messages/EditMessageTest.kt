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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.editMessage
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random.Default.nextBytes

class EditMessageTest {

    /**
     *  A edit message in the raw form consists of bytes in the following order:
     *
     *  (message id bytes (8)) + (updated content text (unlimited))
     */
    private val messageIdToUpdate: MessageId = MessageId()
    private val updatedMessageText: String = "Edited"

    private val bytesProtoMessageData: ByteArray = editMessage {
        this.messageId = messageIdToUpdate.messageIdLong
        this.text = updatedMessageText
    }.toByteArray()

    @Test
    fun shouldThrowBadMessageWhenLengthIsTooLow() {

        // arrange
        val testBlockLazy = {

            // act
            EditMessage.fromByteArray(
                data = bytesProtoMessageData,
                offset = 0,
                length = ProtocolDefines.MESSAGE_ID_LEN - 1
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
            EditMessage.fromByteArray(
                data = bytesProtoMessageData,
                offset = -1,
                length = bytesProtoMessageData.size
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
            EditMessage.fromByteArray(
                data = bytesProtoMessageData,
                offset = 1,
                length = bytesProtoMessageData.size
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
            EditMessage.fromByteArray(
                data = bytesProtoMessageData.copyOfRange(0, bytesProtoMessageData.size - 1),
                offset = 0,
                length = bytesProtoMessageData.size
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldParseValuesCorrectly() {

        // act
        val editMessage = EditMessage.fromByteArray(
            data = bytesProtoMessageData,
            offset = 0,
            length = bytesProtoMessageData.size
        )

        // assert
        assertEquals(messageIdToUpdate.messageIdLong, editMessage.data.messageId)
        assertEquals(updatedMessageText, editMessage.data.text)
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffset() {

        // arrange
        val offset = 5
        val bytesMessageDataContainingOffset = nextBytes(offset) + bytesProtoMessageData

        // act
        val editMessage = EditMessage.fromByteArray(
            data = bytesMessageDataContainingOffset,
            offset = offset,
            length = bytesProtoMessageData.size
        )

        // assert
        assertEquals(messageIdToUpdate.messageIdLong, editMessage.data.messageId)
        assertEquals(updatedMessageText, editMessage.data.text)
    }

    @Test
    fun shouldParseValuesCorrectlyWithOffsetAndTooMuchData() {

        // arrange
        val offset = 5
        val bytesMessageDataContainingOffsetAndJunk =
            nextBytes(offset) + bytesProtoMessageData + nextBytes(10)

        // act
        val editMessage = EditMessage.fromByteArray(
            data = bytesMessageDataContainingOffsetAndJunk,
            offset = offset,
            length = bytesProtoMessageData.size
        )

        // assert
        assertEquals(messageIdToUpdate.messageIdLong, editMessage.data.messageId)
        assertEquals(updatedMessageText, editMessage.data.text)
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
            this.body = bytesProtoMessageData.toByteString()
        }

        // act
        val editMessage = EditMessage.fromReflected(incomingD2DMessage)

        // assert
        assertEquals(incomingMessageId, editMessage.messageId.messageIdLong)
        assertEquals(incomingMessageCreatedAt, editMessage.date.time)
        assertEquals(incomingMessageSenderIdentity, editMessage.fromIdentity)
        assertEquals(messageIdToUpdate.messageIdLong, editMessage.data.messageId)
        assertEquals(updatedMessageText, editMessage.data.text)
    }

    @Test
    fun fromReflectedOutgoingShouldAlsoSetDefaultMessageValues() {

        // arrange
        val outgoingMessageId = 12345678L
        val outgoingMessageCreatedAt: Long = System.currentTimeMillis()
        val outgoingD2DMessage = incomingMessage {
            this.messageId = outgoingMessageId
            this.createdAt = outgoingMessageCreatedAt
            this.body = bytesProtoMessageData.toByteString()
        }

        // act
        val editMessage = EditMessage.fromReflected(outgoingD2DMessage)

        // assert
        assertEquals(outgoingMessageId, editMessage.messageId.messageIdLong)
        assertEquals(outgoingMessageCreatedAt, editMessage.date.time)
        assertEquals(messageIdToUpdate.messageIdLong, editMessage.data.messageId)
        assertEquals(updatedMessageText, editMessage.data.text)
    }
}
