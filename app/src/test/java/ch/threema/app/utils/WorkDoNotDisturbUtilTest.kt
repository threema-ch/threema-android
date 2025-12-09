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

import android.content.SharedPreferences
import ch.threema.app.R
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkDoNotDisturbUtilTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var doNotDisturbUtil: DoNotDisturbUtil

    @BeforeTest
    fun setUp() {
        sharedPreferences = mockk()
        timeProvider = TestTimeProvider(
            // a Thursday at 12:36 GMT
            initialTimestamp = 1_759_408_574_000L,
            timeZoneOffset = ZoneOffset.ofHours(2),
        )
        timeProvider
        doNotDisturbUtil = WorkDoNotDisturbUtil(
            context = mockk {
                every { getString(R.string.preferences__working_days_enable) } returns KEY_DAYS_ENABLED
                every { getString(R.string.preferences__working_days) } returns KEY_WORKING_DAYS
                every { getString(R.string.preferences__work_time_start) } returns KEY_WORK_TIME_START
                every { getString(R.string.preferences__work_time_end) } returns KEY_WORK_TIME_END
            },
            sharedPreferences = sharedPreferences,
            identityProvider = mockk(),
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `not enabled`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns false

        assertFalse(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `enabled but no work days configured`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns null

        assertFalse(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `not a work day`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns setOf(MONDAY, TUESDAY)

        assertTrue(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `work day before work hours`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns setOf(THURSDAY)
        every { sharedPreferences.getString(KEY_WORK_TIME_START, any()) } returns "16:00"
        every { sharedPreferences.getString(KEY_WORK_TIME_END, any()) } returns "23:00"

        assertTrue(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `work day after work hours`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns setOf(THURSDAY)
        every { sharedPreferences.getString(KEY_WORK_TIME_START, any()) } returns "05:00"
        every { sharedPreferences.getString(KEY_WORK_TIME_END, any()) } returns "14:00"

        assertTrue(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `work day inside of work hours`() {
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns setOf(THURSDAY)
        every { sharedPreferences.getString(KEY_WORK_TIME_START, any()) } returns "14:00"
        every { sharedPreferences.getString(KEY_WORK_TIME_END, any()) } returns "23:00"

        assertFalse(doNotDisturbUtil.isDoNotDisturbActive())
    }

    @Test
    fun `sunday is a valid work day`() {
        timeProvider.set(1_759_658_400_000L) // a Sunday
        every { sharedPreferences.getBoolean(KEY_DAYS_ENABLED, false) } returns true
        every { sharedPreferences.getStringSet(KEY_WORKING_DAYS, null) } returns setOf(SUNDAY)
        every { sharedPreferences.getString(KEY_WORK_TIME_START, any()) } returns "10:00"
        every { sharedPreferences.getString(KEY_WORK_TIME_END, any()) } returns "18:00"

        assertFalse(doNotDisturbUtil.isDoNotDisturbActive())
    }

    companion object {
        private const val KEY_DAYS_ENABLED = "working_days_enable"
        private const val KEY_WORKING_DAYS = "working_days"
        private const val KEY_WORK_TIME_START = "work_time_start"
        private const val KEY_WORK_TIME_END = "work_time_end"

        private const val SUNDAY = "0"
        private const val MONDAY = "1"
        private const val TUESDAY = "2"
        private const val THURSDAY = "4"
    }
}
