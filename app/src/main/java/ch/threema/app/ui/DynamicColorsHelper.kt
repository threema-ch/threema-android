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

package ch.threema.app.ui

import android.app.Application
import androidx.preference.PreferenceManager
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

private val logger = LoggingUtil.getThreemaLogger("DynamicColorsHelper")

object DynamicColorsHelper {
    private const val PREF_KEY_DYNAMIC_COLOR = "pref_dynamic_color"

    @JvmStatic
    fun applyDynamicColorsIfEnabled(application: Application) {
        if (!DynamicColors.isDynamicColorAvailable()) {
            logger.info("Dynamic color not available, skipping")
            return
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
            ?: return
        if (sharedPreferences.getBoolean(PREF_KEY_DYNAMIC_COLOR, false)) {
            val dynamicColorsOptions = DynamicColorsOptions.Builder()
                .setPrecondition { _, _ ->
                    sharedPreferences.getBoolean(PREF_KEY_DYNAMIC_COLOR, false)
                }
                .build()
            DynamicColors.applyToActivitiesIfAvailable(application, dynamicColorsOptions)
        }
    }
}
