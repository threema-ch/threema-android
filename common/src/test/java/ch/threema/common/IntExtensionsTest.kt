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

import kotlin.test.Test
import kotlin.test.assertContentEquals

class IntExtensionsTest {
    @Test
    fun `converting ints to unsigned little endian byte representation`() {
        assertContentEquals(byteArrayOf(0.toByte(), 0.toByte()), 0.toU16littleEndian())
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBE.toByte()), 0xBEEF.toU16littleEndian())
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), 0xFFFF.toU16littleEndian())
    }

    @Test
    fun `converting int that are too large truncates to least significant bytes`() {
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBE.toByte()), 0xDEADBEEF.toInt().toU16littleEndian())
    }
}
