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

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.testutils.willThrow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SetProfilePictureMessageTest {

    private val validBlobId = ByteArray(ProtocolDefines.BLOB_ID_LEN).apply { fill(1) }
    private val validSize = ByteArray(4).apply { fill(2) }
    private val validEncryptionKey = ByteArray(ProtocolDefines.BLOB_KEY_LEN).apply { fill(3) }

    private val validBody = validBlobId + validSize + validEncryptionKey

    @Test
    fun testValidBody() {
        val message = SetProfilePictureMessage.fromByteArray(validBody)
        assertContentEquals(validBlobId, message.blobId)
        assertEquals(
            ByteBuffer.wrap(validSize).order(ByteOrder.LITTLE_ENDIAN).getInt(),
            message.size
        )
        assertContentEquals(validEncryptionKey, message.encryptionKey)
    }

    @Test
    fun testValidBodyExplicit() {
        val message = SetProfilePictureMessage.fromByteArray(
            data = validBody,
            offset = 0,
            length = validBody.size,
        )
        assertContentEquals(validBlobId, message.blobId)
        assertEquals(
            ByteBuffer.wrap(validSize).order(ByteOrder.LITTLE_ENDIAN).getInt(),
            message.size
        )
        assertContentEquals(validEncryptionKey, message.encryptionKey)
    }

    @Test
    fun testValidBodyWithOffset() {
        val prefix = ByteArray(10)

        val message = SetProfilePictureMessage.fromByteArray(
            data = prefix + validBody,
            offset = prefix.size,
            length = validBody.size,
        )
        assertContentEquals(validBlobId, message.blobId)
        assertEquals(
            ByteBuffer.wrap(validSize).order(ByteOrder.LITTLE_ENDIAN).getInt(),
            message.size
        )
        assertContentEquals(validEncryptionKey, message.encryptionKey)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenTooSmall() {
        val testBlockLazy = {
            SetProfilePictureMessage.fromByteArray(
                ByteArray(ProtocolDefines.BLOB_ID_LEN)
            )
        }
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenTooBig() {
        val testBlockLazy = {
            SetProfilePictureMessage.fromByteArray(
                ByteArray(ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN + 10)
            )
        }
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionOffsetNegative() {
        val testBlockLazy = {
            SetProfilePictureMessage.fromByteArray(
                data = validBody,
                offset = -1,
                length = validBody.size,
            )
        }
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionUnexpectedLength() {
        val testBlockLazy = {
            SetProfilePictureMessage.fromByteArray(
                data = validSize.copyOf(5),
                offset = 0,
                length = validBody.size,
            )
        }
        testBlockLazy willThrow BadMessageException::class
    }
}
