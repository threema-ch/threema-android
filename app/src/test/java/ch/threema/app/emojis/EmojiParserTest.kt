/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.emojis

import ch.threema.app.emojis.EmojiParser.parseAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmojiParserTest {
    @Test
    fun `parse single 1-codepoint emoji`() {
        val woman = 0x1F469
        val chars = Character.toChars(woman)!!
        val string = String(chars)
        val result = parseAt(string, 0)

        assertNotNull(result)
        assertEquals(2, result.length)
    }

    @Test
    fun `parse emoji with skintone modifier`() {
        val woman = Character.toChars(0x1F469)!!
        val mediumDark = Character.toChars(0x1F3FE)!!
        val string = String(woman) + String(mediumDark)

        val result = parseAt(string, 0)

        assertNotNull(result)
        assertEquals(4, result.length)
    }
}
