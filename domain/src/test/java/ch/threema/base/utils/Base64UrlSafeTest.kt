/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64UrlSafeTest {
    // encodes to
    // 000000 ('A') 111110 ('-') 000001 ('B') 111111 ('_') 011110 ('e') 000000 ('A')
    private val decoded = byteArrayOf(0b00000011, 0b11100000.toByte(), 0b01111111, 0b01111000)

    // decodes to
    // 00000011 11100000 01111111 01111000 0000 (where the last `0000` is discarded)
    private val encoded = "A-B_eA" // "A+B/eA" in base64 encoding

    @Test
    fun testEncode() {
        assertEquals(encoded, Base64UrlSafe.encode(decoded))
    }

    @Test
    fun testDecode() {
        assertContentEquals(decoded, Base64UrlSafe.decode(encoded))
    }

    @Test
    fun testDecodePadded() {
        assertContentEquals(decoded, Base64UrlSafe.decode("$encoded=="))
    }
}
