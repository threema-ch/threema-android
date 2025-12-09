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

import android.content.Context
import android.content.SharedPreferences
import ch.threema.app.R
import ch.threema.app.stores.IdentityProvider
import ch.threema.common.TimeProvider
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

class WorkDoNotDisturbUtil(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    override val identityProvider: IdentityProvider,
    private val timeProvider: TimeProvider,
) : DoNotDisturbUtil() {

    override fun isDoNotDisturbActive(): Boolean {
        val enabled = sharedPreferences.getBoolean(context.getString(R.string.preferences__working_days_enable), false)
        if (!enabled) {
            return false
        }
        val now = timeProvider.getLocal()

        val dayOfWeek = DayOfWeek.of(now.get(ChronoField.DAY_OF_WEEK))
        val workingDays = parseWeekDays(sharedPreferences.getStringSet(context.getString(R.string.preferences__working_days), null))
            ?: return false
        if (dayOfWeek !in workingDays) {
            // today is not a work day
            return true
        }

        val startTime = parseTime(sharedPreferences.getString(context.getString(R.string.preferences__work_time_start), null))
            ?: LocalTime.MIN
        val endTime = parseTime(sharedPreferences.getString(context.getString(R.string.preferences__work_time_end), null))
            ?: LocalTime.MAX
        val currentTime = now.toLocalTime()

        if (currentTime < startTime || currentTime > endTime) {
            return true
        }

        return false
    }

    private fun parseWeekDays(weekDays: Set<String>?): Set<DayOfWeek>? =
        weekDays?.map { weekDay ->
            DayOfWeek.of((weekDay.toInt() + 6) % 7 + 1)
        }
            ?.toSet()

    private fun parseTime(timeString: String?): LocalTime? =
        try {
            timeString?.let(LocalTime::parse)
        } catch (_: DateTimeParseException) {
            null
        }
}
