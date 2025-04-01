/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages

import ch.threema.testutils.willThrow
import kotlin.test.Test
import kotlin.test.assertIs

class ContactRequestProfilePictureMessageTest {

    @Test
    fun testValid() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                ByteArray(0)
            )
        )
    }

    @Test
    fun testValidExplicit() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(0),
                offset = 0,
                length = 0,
            )
        )
    }

    @Test
    fun testNegativeOffset() {
        val testBlockLazy = {
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(0),
                offset = -1,
                length = 0,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testInvalidLength() {
        val testBlockLazy = {
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(42),
                offset = 0,
                length = 42,
            )
        }

        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun testValidWithOffset() {
        assertIs<ContactRequestProfilePictureMessage>(
            ContactRequestProfilePictureMessage.fromByteArray(
                data = ByteArray(1),
                offset = 1,
                length = 0,
            )
        )
    }
}
