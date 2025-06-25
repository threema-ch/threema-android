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

package ch.threema.domain.protocol.csp.messages.groupcall

import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testhelpers.willThrow
import com.google.protobuf.kotlin.toByteString
import java.io.ByteArrayOutputStream
import kotlin.random.Random.Default.nextBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GroupCallStartMessageTest {
    private val groupCreatorIdentityTest = "01234567"
    private val apiGroupIdTest = nextBytes(ProtocolDefines.GROUP_ID_LEN)

    private val protocolVersionTest = 1.toUInt()
    private val gckTest = nextBytes(GroupCallStartData.GCK_LENGTH)
    private val sfuBaseUrlTest = "https://sfu.test.threema.ch"

    private val groupCallStartData = GroupCallStartData(
        protocolVersion = protocolVersionTest,
        gck = gckTest,
        sfuBaseUrl = sfuBaseUrlTest,
    )

    private val groupCallStartMessage = GroupCallStartMessage(groupCallStartData).apply {
        this.groupCreator = groupCreatorIdentityTest
        this.apiGroupId = GroupId(apiGroupIdTest)
    }

    private val bytesGroupCallStartMessage = ByteArrayOutputStream().also { bos ->
        bos.write(groupCallStartMessage.body)
    }.toByteArray()

    /**
     *  creator identity length = *8*, api groupId length = *8*
     */
    @Test
    fun shouldThrowBadMessageExceptionWhenLengthBelowIdentityAndGroupIdLength() {
        // arrange
        val testBlockLazy = {
            // act
            GroupCallStartMessage.fromByteArray(
                data = bytesGroupCallStartMessage,
                offset = 0,
                length = 10,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    /**
     *  creator identity length = *8*, api groupId length = *8*
     */
    @Test
    fun shouldThrowBadMessageExceptionWhenLengthEqualsIdentityAndGroupIdLength() {
        // arrange
        val testBlockLazy = {
            // act
            GroupCallStartMessage.fromByteArray(
                data = bytesGroupCallStartMessage,
                offset = 0,
                length = 16,
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
            GroupCallStartMessage.fromByteArray(
                data = bytesGroupCallStartMessage,
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
            GroupCallStartMessage.fromByteArray(
                data = bytesGroupCallStartMessage,
                offset = 0,
                length = bytesGroupCallStartMessage.size + 1,
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
            GroupCallStartMessage.fromByteArray(
                data = bytesGroupCallStartMessage,
                offset = 1,
                length = bytesGroupCallStartMessage.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultGroupCallStartMessage = GroupCallStartMessage.fromByteArray(
            data = bytesGroupCallStartMessage,
            offset = 0,
            length = bytesGroupCallStartMessage.size,
        )

        // assert
        assertEquals(groupCreatorIdentityTest, resultGroupCallStartMessage.groupCreator)
        assertContentEquals(apiGroupIdTest, resultGroupCallStartMessage.apiGroupId.groupId)
        assertEquals(groupCallStartData, resultGroupCallStartMessage.data)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + bytesGroupCallStartMessage

        // act
        val resultGroupCallStartMessage = GroupCallStartMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = dataWithOffsetByte.size - 1,
        )

        // assert
        assertEquals(groupCreatorIdentityTest, resultGroupCallStartMessage.groupCreator)
        assertContentEquals(apiGroupIdTest, resultGroupCallStartMessage.apiGroupId.groupId)
        assertEquals(groupCallStartData, resultGroupCallStartMessage.data)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + bytesGroupCallStartMessage

        val testBlockLazy = {
            // act
            GroupFileMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = dataWithOffsetByte.size - 1,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun fromReflectedIncomingShouldParseBodyAndSetCommonMessageProperties() {
        // arrange
        val incomingMessageId = 12345678L
        val incomingMessageCreatedAt: Long = System.currentTimeMillis()
        val incomingMessageSenderIdentity = "01234567"
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = incomingMessageSenderIdentity
            this.messageId = incomingMessageId
            this.createdAt = incomingMessageCreatedAt
            this.body = bytesGroupCallStartMessage.toByteString()
        }

        // act
        val resultGroupCallStartMessage = GroupCallStartMessage.fromReflected(incomingD2DMessage)

        // assert
        assertEquals(incomingMessageId, resultGroupCallStartMessage.messageId.messageIdLong)
        assertEquals(incomingMessageCreatedAt, resultGroupCallStartMessage.date.time)
        assertEquals(incomingMessageSenderIdentity, resultGroupCallStartMessage.fromIdentity)
        assertEquals(groupCreatorIdentityTest, resultGroupCallStartMessage.groupCreator)
        assertContentEquals(apiGroupIdTest, resultGroupCallStartMessage.apiGroupId.groupId)
        assertEquals(groupCallStartData, resultGroupCallStartMessage.data)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonMessageProperties() {
        // arrange
        val outgoingMessageId = 12345678L
        val outgoingMessageCreatedAt: Long = System.currentTimeMillis()
        val outgoingMessageSenderIdentity = "01234567"
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId
            this.createdAt = outgoingMessageCreatedAt
            this.body = bytesGroupCallStartMessage.toByteString()
        }

        // act
        val resultGroupCallStartMessage = GroupCallStartMessage.fromReflected(
            message = outgoingD2DMessage,
            ownIdentity = outgoingMessageSenderIdentity,
        )

        // assert
        assertEquals(outgoingMessageId, resultGroupCallStartMessage.messageId.messageIdLong)
        assertEquals(outgoingMessageCreatedAt, resultGroupCallStartMessage.date.time)
        assertEquals(outgoingMessageSenderIdentity, resultGroupCallStartMessage.fromIdentity)
        assertEquals(groupCreatorIdentityTest, resultGroupCallStartMessage.groupCreator)
        assertContentEquals(apiGroupIdTest, resultGroupCallStartMessage.apiGroupId.groupId)
        assertEquals(groupCallStartData, resultGroupCallStartMessage.data)
    }
}
