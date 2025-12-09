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

package ch.threema.common

import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByteArrayExtensionsTest {
    @Test
    fun `build byte array`() {
        val bytes = buildByteArray {
            write(0x12)
            write(0x34)
            write(0x56)
            write(0x78)
        }
        assertContentEquals(
            byteArrayOf(0x12, 0x34, 0x56, 0x78),
            bytes,
        )
    }

    @Test
    fun `xor of two byte arrays`() {
        val array1 = byteArrayOf(1, 2, 5, 0, 32, 48)
        val array2 = byteArrayOf(0, 3, 5, 100, 20, 1)

        assertContentEquals(
            byteArrayOf(1, 1, 0, 100, 52, 49),
            array1 xor array2,
        )
    }

    @Test
    fun `xor of two byte arrays fails if they are not the same length`() {
        val array1 = byteArrayOf(1, 2, 3)
        val array2 = byteArrayOf(1, 2)

        assertFailsWith<IllegalArgumentException> { array1 xor array2 }
    }

    @Test
    fun `byte array equality`() {
        assertTrue(byteArrayOf(1, 2, 3).secureContentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(byteArrayOf(1, 2, 4).secureContentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(byteArrayOf(1, 2, 3, 4).secureContentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(byteArrayOf(1, 2).secureContentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `byte array to cryptographic byte array`() {
        assertContentEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3).toCryptographicByteArray().value)
    }

    @Test
    fun `chunked empty byte array`() {
        assertTrue(emptyByteArray().chunked(1).isEmpty())
    }

    @Test
    fun `chunked into equal parts`() {
        val chunks = byteArrayOf(1, 2, 3, 4, 5, 6).chunked(3)

        assertEquals(2, chunks.size)
        assertContentEquals(byteArrayOf(1, 2, 3), chunks[0])
        assertContentEquals(byteArrayOf(4, 5, 6), chunks[1])
    }

    @Test
    fun `chunked into differently sized parts`() {
        val a = byteArrayOf(1).chunked(3)
        assertEquals(1, a.size)
        assertContentEquals(byteArrayOf(1), a[0])

        val b = byteArrayOf(1, 2).chunked(3)
        assertEquals(1, b.size)
        assertContentEquals(byteArrayOf(1, 2), b[0])

        val c = byteArrayOf(1, 2, 3, 4).chunked(3)
        assertEquals(2, c.size)
        assertContentEquals(byteArrayOf(1, 2, 3), c[0])
        assertContentEquals(byteArrayOf(4), c[1])

        val d = byteArrayOf(1, 2, 3, 4, 5).chunked(3)
        assertEquals(2, d.size)
        assertContentEquals(byteArrayOf(1, 2, 3), d[0])
        assertContentEquals(byteArrayOf(4, 5), d[1])
    }

    @Test
    fun `byte array to hex string`() {
        val bytes = byteArrayOf(0, 1, 7, 30, 32, 50, 65, 128.toByte(), 254.toByte(), 255.toByte())
        assertEquals(
            "0001071e20324180feff",
            bytes.toHexString(),
        )
    }

    @Test
    fun `byte array to hex string truncated to max size`() {
        val bytes = byteArrayOf(0, 1, 7, 30, 32, 50, 65, 128.toByte(), 254.toByte(), 255.toByte())
        assertEquals(
            "0001071e2032…",
            bytes.toHexString(maxBytes = 6),
        )
    }

    @Test
    fun `byte array to little-endian long`() {
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        val long = bytes.toLong(order = ByteOrder.LITTLE_ENDIAN)
        assertEquals("220f66cdff10a001".hexToLong(), long)
    }

    @Test
    fun `byte array to big-endian long`() {
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        val long = bytes.toLong(order = ByteOrder.BIG_ENDIAN)
        assertEquals("01a010ffcd660f22".hexToLong(), long)
    }

    @Test
    fun `read little-endian integer`() {
        val bytes = byteArrayOf(0x01, 0x12, 0x34, 0x45, 0x56, 0x67)
        assertEquals(
            0x56453412,
            bytes.readLittleEndianInt(offset = 1),
        )
    }

    @Test
    fun `read little-endian short`() {
        val bytes = byteArrayOf(0x01, 0x12, 0x34, 0x45)
        assertEquals(
            0x3412.toShort(),
            bytes.readLittleEndianShort(offset = 1),
        )
    }
}
