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

import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class TimeExtensionsTest {
    @Test
    fun `difference between dates`() {
        val date1 = Date(11L * 60 * 60 * 1000)
        val date2 = Date(9L * 60 * 60 * 1000)

        assertEquals(2.hours, date1 - date2)
    }

    @Test
    fun `date minus a duration`() {
        val date1 = Date(11L * 60 * 60 * 1000)
        val date2 = Date(9L * 60 * 60 * 1000)

        assertEquals(date2, date1 - 2.hours)
    }

    @Test
    fun `date plus a duration`() {
        val date1 = Date(11L * 60 * 60 * 1000)
        val date2 = Date(9L * 60 * 60 * 1000)

        assertEquals(date1, date2 + 2.hours)
    }

    @Test
    fun `difference between instants`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(2.hours, instant1 - instant2)
    }

    @Test
    fun `instant minus a duration`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(instant2, instant1 - 2.hours)
    }

    @Test
    fun `instant plus a duration`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(instant1, instant2 + 2.hours)
    }
}
