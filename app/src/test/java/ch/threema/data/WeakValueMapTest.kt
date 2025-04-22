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

package ch.threema.data

import java.util.Date
import kotlin.test.Test
import org.junit.Assert

class WeakValueMapTest {
    @Test
    fun testReferences() {
        val map = WeakValueMap<String, Date>()
        val date1 = map.getOrCreate("hello") { Date() }
        val date2 = map.getOrCreate("hello") { Date() }
        val date3 = map.getOrCreate("world") { Date() }
        Assert.assertSame(date1, date2)
        Assert.assertNotSame(date1, date3)
        Assert.assertSame(date1, map.get("hello"))
        Assert.assertSame(date3, map.get("world"))
        Assert.assertNull(map.get("something-else"))
    }

    @Test
    fun testMissNull() {
        val map = WeakValueMap<String, String>()
        val string1 = map.getOrCreate("hello") { "guten tag!" }
        val string2 = map.getOrCreate("not-found") { null }
        Assert.assertEquals("guten tag!", string1)
        Assert.assertNull(string2)
    }
}
