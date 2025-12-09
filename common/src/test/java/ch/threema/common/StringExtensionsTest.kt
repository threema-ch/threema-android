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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtensionsTest {
    @Test
    fun `string without last line`() {
        assertEquals("", "Hello World".withoutLastLine())
        assertEquals("Hello", "Hello\nWorld".withoutLastLine())
        assertEquals("Hello\nWorld", "Hello\nWorld\n".withoutLastLine())
        assertEquals("", "".withoutLastLine())
    }

    @Test
    fun `last line of string`() {
        assertEquals("Hello World", "Hello World".lastLine())
        assertEquals("World", "Hello\nWorld".lastLine())
        assertEquals("", "Hello\nWorld\n".lastLine())
        assertEquals("", "".lastLine())
    }

    @Test
    fun `take string unless empty`() {
        assertEquals("test", "test".takeUnlessEmpty())
        assertNull("".takeUnlessEmpty())
    }

    @Test
    fun `take string unless blank`() {
        assertEquals("test", "test".takeUnlessBlank())
        assertEquals(" test ", " test ".takeUnlessBlank())
        assertEquals(" te st ", " te st ".takeUnlessBlank())
        assertNull("".takeUnlessBlank())
        assertNull("  ".takeUnlessBlank())
    }

    @Test
    fun `test capitalize`() {
        assertEquals("Hello worlD", "hello worlD".capitalize())
        assertEquals("Hello World", "Hello World".capitalize())
        assertEquals("Äöü", "äöü".capitalize())
    }
}
