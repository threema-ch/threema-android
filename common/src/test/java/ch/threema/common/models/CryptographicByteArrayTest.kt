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

package ch.threema.common.models

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CryptographicByteArrayTest {
    @Test
    fun `equality of 2 arrays`() {
        val array1 = CryptographicByteArray(byteArrayOf(1, 2, 3))
        val array2 = CryptographicByteArray(byteArrayOf(1, 2, 3))
        assertEquals(array1, array2)
    }

    @Test
    fun `inequality of 2 arrays`() {
        val array1 = CryptographicByteArray(byteArrayOf(1, 2, 3))
        val array2 = CryptographicByteArray(byteArrayOf(1, 2, 3, 4))
        assertNotEquals(array1, array2)
    }

    @Test
    fun deconstruction() {
        val (byteArray) = CryptographicByteArray(byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), byteArray)
    }

    @Test
    fun `toString does not reveal the whole array`() {
        val array = CryptographicByteArray(byteArrayOf(33, 10, 3, 100, 1, 2, 3, 4))
        assertEquals("[8 bytes: 21,0a,03,...]", array.toString())
    }
}
