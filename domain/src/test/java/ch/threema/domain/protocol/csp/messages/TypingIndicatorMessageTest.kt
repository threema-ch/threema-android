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

import ch.threema.domain.models.MessageId
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.testutils.willThrow
import com.google.protobuf.kotlin.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypingIndicatorMessageTest {
    private val isTypingBytes = byteArrayOf(1)
    private val isNotTypingBytes = byteArrayOf(0)

    @Test
    fun testValidTyping() {
        val typingIndicatorMessage = TypingIndicatorMessage.fromByteArray(isTypingBytes)
        assertTrue { typingIndicatorMessage.isTyping }
    }

    @Test
    fun testValidNotTyping() {
        val typingIndicatorMessage = TypingIndicatorMessage.fromByteArray(isNotTypingBytes)
        assertFalse { typingIndicatorMessage.isTyping }
    }

    @Test
    fun testValidOffset() {
        val prependedBytes = byteArrayOf(0, 1, 2)

        val typingIndicatorMessage = TypingIndicatorMessage.fromByteArray(
            data = prependedBytes + isTypingBytes,
            offset = prependedBytes.size,
            length = 1,
        )

        assertTrue { typingIndicatorMessage.isTyping }
    }

    @Test
    fun testValidOffsetWithTooMuchData() {
        val prependedBytes = byteArrayOf(0, 1, 2)
        val appendedBytes = byteArrayOf(3, 4)

        val typingIndicatorMessage = TypingIndicatorMessage.fromByteArray(
            data = prependedBytes + isTypingBytes + appendedBytes,
            offset = prependedBytes.size,
            length = 1,
        )

        assertTrue { typingIndicatorMessage.isTyping }
    }

    @Test
    fun testNegativeOffset() {
        val testBlock = {
            TypingIndicatorMessage.fromByteArray(
                data = ByteArray(0),
                offset = -1,
                length = 0,
            )
        }

        testBlock willThrow BadMessageException::class
    }

    @Test
    fun testInvalidLength() {
        val testBlockLazy = {
            TypingIndicatorMessage.fromByteArray(
                data = ByteArray(42),
                offset = 0,
                length = 42,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testMessagePropertiesInitialized() {
        val fromIdentity = "01234567"
        val messageId = MessageId()
        val createdAt = 42424242L

        val incomingTypingIndicatorMessage = incomingMessage {
            this.senderIdentity = fromIdentity
            this.messageId = messageId.messageIdLong
            this.createdAt = createdAt
            this.body = isNotTypingBytes.toByteString()
        }

        val typingIndicatorMessage =
            TypingIndicatorMessage.fromReflected(incomingTypingIndicatorMessage)

        assertFalse { typingIndicatorMessage.isTyping }
        assertEquals(fromIdentity, typingIndicatorMessage.fromIdentity)
        assertEquals(messageId, typingIndicatorMessage.messageId)
        assertEquals(createdAt, typingIndicatorMessage.date.time)
    }
}
