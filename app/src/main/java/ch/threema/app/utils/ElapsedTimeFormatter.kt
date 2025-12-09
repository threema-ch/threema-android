/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import ch.threema.app.R
import ch.threema.common.toHMMSS
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object ElapsedTimeFormatter {
    @JvmStatic
    fun secondsToString(seconds: Long): String =
        seconds.seconds.toHMMSS()

    @JvmStatic
    fun millisecondsToString(milliseconds: Long): String =
        milliseconds.milliseconds.toHMMSS()

    @JvmStatic
    @Deprecated("Does not take locale's grammar such as word order or plurality into account, thus may produce wrong results.")
    fun getDurationStringHuman(context: Context, fullSeconds: Long): String =
        with(context) {
            fullSeconds.seconds.toComponents { minutes, seconds, _ ->
                if (minutes == 0L) {
                    "$seconds ${getString(R.string.seconds)}"
                } else {
                    "$minutes ${getString(R.string.minutes)} ${getString(R.string.and)} $seconds ${getString(R.string.seconds)}"
                }
            }
        }
}
