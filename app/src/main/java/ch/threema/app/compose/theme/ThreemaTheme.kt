/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.compose.theme.color.ColorsDark
import ch.threema.app.compose.theme.color.ColorsLight
import ch.threema.app.compose.theme.color.CustomColor
import ch.threema.app.compose.theme.color.CustomColorDark
import ch.threema.app.compose.theme.color.CustomColorLight
import ch.threema.app.compose.theme.color.LocalCustomColor

val AppTypography = Typography() // system default

@Composable
fun ThreemaTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val shouldUseDynamicColors: Boolean = remember {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())
        sharedPreferences.getBoolean("pref_dynamic_color", false)
    }

    val materialColorScheme: ColorScheme = when {
        shouldUseDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (isDarkTheme) ColorsDark.toColorScheme() else ColorsLight.toColorScheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = materialColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !isDarkTheme
        }
    }

    val customColorScheme = if (isDarkTheme) CustomColorDark else CustomColorLight
    CompositionLocalProvider(
        LocalCustomColor provides customColorScheme
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

@Suppress("UnusedReceiverParameter")
val MaterialTheme.customColorScheme: CustomColor
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColor.current
