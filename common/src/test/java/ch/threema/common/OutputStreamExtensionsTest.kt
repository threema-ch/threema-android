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

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class OutputStreamExtensionsTest {
    @Test
    fun `write little-endian integer`() {
        val outputStream = ByteArrayOutputStream()
        outputStream.writeLittleEndianInt(0x1234f045)
        outputStream.writeLittleEndianInt(0x1afbfcfd)
        assertContentEquals(
            byteArrayOf(0x45, 0xf0.toByte(), 0x34, 0x12, 0xfd.toByte(), 0xfc.toByte(), 0xfb.toByte(), 0x1a),
            outputStream.toByteArray(),
        )
    }

    @Test
    fun `write little-endian short`() {
        val outputStream = ByteArrayOutputStream()
        outputStream.writeLittleEndianShort(0xf045.toShort())
        outputStream.writeLittleEndianShort(0x1234.toShort())
        assertContentEquals(
            byteArrayOf(0x45, 0xf0.toByte(), 0x34, 0x12),
            outputStream.toByteArray(),
        )
    }
}
