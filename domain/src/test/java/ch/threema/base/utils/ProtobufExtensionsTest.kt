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

package ch.threema.base.utils

import ch.threema.common.toCryptographicByteArray
import com.google.protobuf.ByteString
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ProtobufExtensionsTest {
    @Test
    fun `cryptographic array to byte string`() {
        assertEquals(
            ByteString.copyFrom(byteArrayOf(1, 2, 3)),
            byteArrayOf(1, 2, 3).toCryptographicByteArray().toByteString(),
        )
    }

    @Test
    fun `byte string to cryptographic array`() {
        assertEquals(
            byteArrayOf(1, 2, 3).toCryptographicByteArray(),
            ByteString.copyFrom(byteArrayOf(1, 2, 3)).toCryptographicByteArray(),
        )
    }

    @Test
    fun `generate random padding`() {
        val secureRandomMock = mockk<SecureRandom> {
            every { nextInt(256) } returns 10
        }

        assertContentEquals(
            byteArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
            generateRandomProtobufPadding(secureRandomMock).toByteArray(),
        )
    }
}
