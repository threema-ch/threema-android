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

import ch.threema.testhelpers.mockSecureRandom
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SecureRandomExtensionsTest {
    @Test
    fun `generate random bytes`() {
        val secureRandomMock = mockSecureRandom()

        val bytes1 = secureRandomMock.generateRandomBytes(4)
        assertContentEquals(byteArrayOf(0, 1, 2, 3), bytes1)

        val bytes2 = secureRandomMock.generateRandomBytes(300)
        assertContentEquals(ByteArray(300) { it.toByte() }, bytes2)
    }

    @Test
    fun `generate unsigned long`() {
        val secureRandomMock = mockk<SecureRandom> {
            every { nextLong() } returns 1_234_567_890_123_456_789L
        }

        assertEquals(
            1_234_567_890_123_456_789UL,
            secureRandomMock.nextULong(),
        )
    }
}
