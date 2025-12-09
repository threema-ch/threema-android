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

class IntExtensionsTest {
    @Test
    fun `int to little-endian`() {
        val int = 0xaffe1234.toInt()
        val bytes = int.toByteArray(order = ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(
            byteArrayOf(0x34, 0x12, 0xfe.toByte(), 0xaf.toByte()),
            bytes,
        )
    }

    @Test
    fun `int to big-endian`() {
        val int = 0xaffe1234.toInt()
        val bytes = int.toByteArray(order = ByteOrder.BIG_ENDIAN)
        assertContentEquals(
            byteArrayOf(0xaf.toByte(), 0xfe.toByte(), 0x12, 0x34),
            bytes,
        )
    }

    @Test
    fun `round up to power of 2`() {
        assertEquals(0, 0.roundUpToPowerOfTwo())
        assertEquals(1, 1.roundUpToPowerOfTwo())
        assertEquals(2, 2.roundUpToPowerOfTwo())
        assertEquals(4, 3.roundUpToPowerOfTwo())
        assertEquals(4, 4.roundUpToPowerOfTwo())
        assertEquals(16, 12.roundUpToPowerOfTwo())
        assertEquals(32, 20.roundUpToPowerOfTwo())
        assertEquals(32, 32.roundUpToPowerOfTwo())
        assertEquals(64, 63.roundUpToPowerOfTwo())
        assertEquals(128, 127.roundUpToPowerOfTwo())
        assertEquals(256, 255.roundUpToPowerOfTwo())
        assertEquals(0x10000, 0xFFFF.roundUpToPowerOfTwo())
        assertEquals(0x100000, 0xFFFFF.roundUpToPowerOfTwo())
        assertEquals(0x100000, 0x100000.roundUpToPowerOfTwo())
        assertEquals(0x10000000, 0x10000000.roundUpToPowerOfTwo())
    }
}
