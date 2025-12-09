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

class LongExtensionsTest {
    @Test
    fun `little-endian long to byte array`() {
        val long = "220f66cdff10a001".hexToLong()
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        assertContentEquals(bytes, long.toByteArray(order = ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `big-endian long to byte array`() {
        val long = "01a010ffcd660f22".hexToLong()
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        assertContentEquals(bytes, long.toByteArray(order = ByteOrder.BIG_ENDIAN))
    }
}
