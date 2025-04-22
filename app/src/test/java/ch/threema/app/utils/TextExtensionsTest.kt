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

package ch.threema.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextExtensionsTest {

    @Test
    fun `test capitalize`() {
        assertEquals("Hello worlD", "hello worlD".capitalize())
        assertEquals("Hello World", "Hello World".capitalize())
        assertEquals("Äöü", "äöü".capitalize())
    }

    @Test
    fun `test truncate`() {
        assertEquals("Hello World", "Hello World".truncate(maxLength = 12))
        assertEquals("Hello Worl…", "Hello World".truncate(maxLength = 10))
        assertEquals("Hello …", "Hello World".truncate(maxLength = 6))
        assertEquals("", "".truncate(maxLength = 6))
        assertEquals("…", "Hello World".truncate(maxLength = 0))
        assertFailsWith<IllegalArgumentException> {
            "Hello World".truncate(maxLength = -1)
        }
    }
}
