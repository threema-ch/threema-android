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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import ch.threema.app.compose.theme.color.ColorsDark
import ch.threema.app.compose.theme.color.ColorsLight

val AppTypography = Typography() // system default

/**
 *  The material theme that respects our self handled setting for use of dynamic colors
 */
@Composable
fun ThreemaTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val shouldUseDynamicColors = remember {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.getBoolean("pref_dynamic_color", false)
    }

    ThreemaThemeBase(
        isDarkTheme = isDarkTheme,
        shouldUseDynamicColors = shouldUseDynamicColors,
        content = content,
    )
}

@Composable
private fun ThreemaThemeBase(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldUseDynamicColors: Boolean,
    content: @Composable () -> Unit,
) {
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = materialColorScheme,
        typography = AppTypography,
        content = content,
    )
}

/**
 * This is just for use in `@Preview`s.
 */
@Composable
fun ThreemaThemePreview(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldUseDynamicColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    ThreemaThemeBase(
        isDarkTheme = isDarkTheme,
        shouldUseDynamicColors = shouldUseDynamicColors,
        content = content,
    )
}
