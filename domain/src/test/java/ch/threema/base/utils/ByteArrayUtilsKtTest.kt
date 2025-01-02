/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.base.utils

import org.junit.Assert.*
import org.junit.Test

class ByteArrayUtilsKtTest {
    @Test
    fun testChunkedEmptyArray() {
        assertTrue(byteArrayOf().chunked(1).isEmpty())
    }

    @Test
    fun testChunkedEqualParts() {
        val chunks = byteArrayOf(1, 2, 3, 4, 5, 6).chunked(3)

        assertEquals(2, chunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), chunks[0])
        assertArrayEquals(byteArrayOf(4, 5, 6), chunks[1])
    }

    @Test
    fun testChunkedWithDifferentSizedParts() {
        val a = byteArrayOf(1).chunked(3)
        assertEquals(1, a.size)
        assertArrayEquals(byteArrayOf(1), a[0])

        val b = byteArrayOf(1, 2).chunked(3)
        assertEquals(1, b.size)
        assertArrayEquals(byteArrayOf(1, 2), b[0])

        val c = byteArrayOf(1, 2, 3, 4).chunked(3)
        assertEquals(2, c.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), c[0])
        assertArrayEquals(byteArrayOf(4), c[1])

        val d = byteArrayOf(1, 2, 3, 4, 5).chunked(3)
        assertEquals(2, d.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), d[0])
        assertArrayEquals(byteArrayOf(4, 5), d[1])
    }
}
