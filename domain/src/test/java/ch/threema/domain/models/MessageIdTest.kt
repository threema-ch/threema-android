/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.domain.models

import ch.threema.base.ThreemaException
import ch.threema.testhelpers.mockSecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MessageIdTest {

    @Test
    fun `create from string`() {
        val messageId = MessageId.fromString("ffba11ad00123456")

        assertEquals(
            MessageId(
                byteArrayOf(
                    0xff.toByte(),
                    0xba.toByte(),
                    0x11.toByte(),
                    0xad.toByte(),
                    0x00.toByte(),
                    0x12.toByte(),
                    0x34.toByte(),
                    0x56.toByte(),
                ),
            ),
            messageId,

        )
    }

    @Test
    fun `cannot create from invalid string`() {
        assertFailsWith<ThreemaException> {
            MessageId.fromString("ffba11ax00123456")
        }
    }

    @Test
    fun `cannot create from null string`() {
        assertFailsWith<ThreemaException> {
            MessageId.fromString(null)
        }
    }

    @Test
    fun `cannot create from string of wrong length`() {
        assertFailsWith<ThreemaException> {
            MessageId.fromString("112233")
        }
    }

    @Test
    fun `create from array`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val messageId = MessageId(bytes)
        assertContentEquals(bytes, messageId.messageId)
    }

    @Test
    fun `cannot create from array of wrong length`() {
        assertFailsWith<IllegalArgumentException> {
            MessageId(byteArrayOf(1, 2, 3, 4))
        }
    }

    @Test
    fun `create from array with offset`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val messageId = MessageId(data = bytes, offset = 1)
        assertContentEquals(byteArrayOf(2, 3, 4, 5, 6, 7, 8, 9), messageId.messageId)
    }

    @Test
    fun `cannot create from array with offset if source is too short`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        assertFailsWith<IndexOutOfBoundsException> {
            MessageId(data = bytes, offset = 6)
        }
    }

    @Test
    fun `random message id is generated from secure random`() {
        val random = mockSecureRandom()
        assertEquals(
            MessageId(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)),
            MessageId.random(random),
        )
    }

    @Test
    fun `get message id as long uses little-endian`() {
        val bytes = byteArrayOf(
            0x10.toByte(),
            0x20.toByte(),
            0x30.toByte(),
            0x40.toByte(),
            0x50.toByte(),
            0x60.toByte(),
            0x70.toByte(),
            0x80.toByte(),
        )
        val messageId = MessageId(bytes)
        assertEquals("8070605040302010".hexToLong(), messageId.messageIdLong)
    }

    @Test
    fun `create from little-endian encoded long`() {
        val messageId = MessageId("807060504030201a".hexToLong())
        assertEquals(
            MessageId(
                byteArrayOf(
                    0x1a.toByte(),
                    0x20.toByte(),
                    0x30.toByte(),
                    0x40.toByte(),
                    0x50.toByte(),
                    0x60.toByte(),
                    0x70.toByte(),
                    0x80.toByte(),
                ),
            ),
            messageId,
        )
    }

    @Test
    fun `to string uses lower-case hex encoding`() {
        val bytes = byteArrayOf(
            0x1a.toByte(),
            0x2b.toByte(),
            0x3c.toByte(),
            0x4d.toByte(),
            0x5e.toByte(),
            0x6f.toByte(),
            0x70.toByte(),
            0x80.toByte(),
        )
        val messageId = MessageId(bytes)
        assertEquals("1a2b3c4d5e6f7080", messageId.toString())
    }

    @Test
    fun inequality() {
        val messageId1 = MessageId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val messageId2 = MessageId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 0))
        assertNotEquals(messageId1, messageId2)
    }

    @Test
    fun `hashCode is the same for equal instances`() {
        val messageId1 = MessageId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val messageId2 = MessageId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(messageId1.hashCode(), messageId2.hashCode())
    }

    @Test
    fun `hashCode is the different for different instances`() {
        val messageId1 = MessageId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val messageId2 = MessageId(byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16))
        assertNotEquals(messageId1.hashCode(), messageId2.hashCode())
    }
}
