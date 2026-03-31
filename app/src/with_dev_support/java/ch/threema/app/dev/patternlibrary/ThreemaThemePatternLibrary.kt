package ch.threema.app.dev.patternlibrary

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import ch.threema.app.compose.theme.ThreemaThemeBase

/**
 * This is just for use in the pattern library (can override the users dynamic colors setting).
 *
 * @param shouldUseDynamicColorsOverride If set to `null`, the user's setting will be used
 */
@Composable
fun ThreemaThemePatternLibrary(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldUseDynamicColorsOverride: Boolean?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val shouldUseDynamicColors = remember(shouldUseDynamicColorsOverride) {
        shouldUseDynamicColorsOverride
            ?: PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean("pref_dynamic_color", false)
    }

    ThreemaThemeBase(
        isDarkTheme = isDarkTheme,
        shouldUseDynamicColors = shouldUseDynamicColors,
        content = content,
    )
}
