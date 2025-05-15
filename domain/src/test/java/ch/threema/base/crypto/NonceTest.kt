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

package ch.threema.base.crypto

import com.neilalexander.jnacl.NaCl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.assertThrows

class NonceTest {
    @Test
    fun testValidNonce() {
        // Test valid nonce creation
        Nonce(generateNonceBytes())
    }

    @Test
    fun testTooLongNonce() {
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(32))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(25))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(48))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(64))
        }
    }

    @Test
    fun testTooShortNonce() {
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(23))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(16))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(12))
        }
        assertThrows<IllegalArgumentException> {
            Nonce(generateBytes(0))
        }
    }

    @Test
    fun testHashedNonceCreation() {
        // Test valid hashed nonce creation
        HashedNonce(generateNonceHashBytes())
    }

    @Test
    fun testTooLongNonceHash() {
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(33))
        }
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(64))
        }
    }

    @Test
    fun testTooShortNonceHash() {
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(31))
        }
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(24))
        }
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(16))
        }
        assertThrows<IllegalArgumentException> {
            HashedNonce(generateBytes(0))
        }
    }

    /**
     * This test is primarily used to detect whether the hashing implementation has changed.
     */
    @Test
    fun testHashing() {
        // Arrange
        val nonce = Nonce(ByteArray(NaCl.NONCEBYTES))
        val identity = "01234567"

        // Act
        val hashedNonce = nonce.hashNonce(identity)

        // Assert
        assertContentEquals(
            byteArrayOf(
                -36, -70, 41, -67, 95, 116, 25, 113, 49, -64, -26, 69, -93, 2, -103, -70, -125,
                50, 99, -81, 104, -20, -10, -5, 127, -33, 57, -120, -7, -13, -50, 78,
            ),
            hashedNonce.bytes,
        )
    }

    private fun generateNonceBytes(): ByteArray = generateBytes(NaCl.NONCEBYTES)

    private fun generateNonceHashBytes(): ByteArray = generateBytes(32)

    private fun generateBytes(length: Int) = ByteArray(length) { it.toByte() }
}
