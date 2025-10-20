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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrayExtensionsTest {
    @Test
    fun `comparing arrays ignoring order`() {
        assertTrue(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2, 3)))
        assertTrue(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(2, 1, 3)))
        assertTrue(arrayOf(1, 2, 3, 2).equalsIgnoreOrder(arrayOf(2, 1, 3, 2)))
        assertFalse(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2)))
        assertFalse(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2, 4)))
    }
}
