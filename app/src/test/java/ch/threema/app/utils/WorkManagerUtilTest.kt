/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import org.junit.Test

import org.junit.Assert.*



class WorkManagerUtilTest {

    /**
     * Periods in seconds less than or equal to zero should be normalized to one day in milliseconds.
     */
    @Test
    fun normalizeSchedulePeriodLessThanOrEqualZero() {
        val dayInMillis = 24L * 60 * 60 * 1000
        listOf(Int.MIN_VALUE, -1, 0)
            .forEach {
                assertEquals(dayInMillis, WorkManagerUtil.normalizeSchedulePeriod(it))
            }
    }

    /**
     * Periods in seconds larger than zero must be normalized to the same period in milliseconds.
     */
    @Test
    fun normalizeSchedulePeriodGreaterThanZero() {
        listOf(1, 10, Int.MAX_VALUE)
            .forEach {
                val expected = it * 1000L
                assertEquals(expected, WorkManagerUtil.normalizeSchedulePeriod(it))
            }
    }
}
