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

package ch.threema.protobuf

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ProtobufTest {
    @Test
    fun `test combineEncryptedDataAndNonce`() {
        val combined = combineEncryptedDataAndNonce(
            data = DATA,
            nonce = NONCE,
        )

        assertContentEquals(COMBINED, combined)
    }

    @Test
    fun `test separateEncryptedDataAndNonce`() {
        val (data, nonce) = separateEncryptedDataAndNonce(COMBINED)

        assertContentEquals(DATA, data)
        assertContentEquals(NONCE, nonce)
    }

    companion object {
        private val DATA = byteArrayOf(1, 2, 3, 4)
        private val NONCE = byteArrayOf(9, 8, 7, 6)
        private val COMBINED = byteArrayOf(10, 4, 9, 8, 7, 6, 18, 4, 1, 2, 3, 4)
    }
}
