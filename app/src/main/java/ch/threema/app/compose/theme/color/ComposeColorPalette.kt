package ch.threema.app.compose.theme.color

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

interface ComposeColorPalette {
    val primary: Color
    val onPrimary: Color
    val primaryContainer: Color
    val onPrimaryContainer: Color
    val secondary: Color
    val onSecondary: Color
    val secondaryContainer: Color
    val onSecondaryContainer: Color
    val tertiary: Color
    val onTertiary: Color
    val tertiaryContainer: Color
    val onTertiaryContainer: Color
    val error: Color
    val onError: Color
    val errorContainer: Color
    val onErrorContainer: Color
    val background: Color
    val onBackground: Color
    val surface: Color
    val onSurface: Color
    val surfaceVariant: Color
    val onSurfaceVariant: Color
    val surfaceContainerLowest: Color
    val surfaceContainerLow: Color
    val surfaceContainer: Color
    val surfaceContainerHigh: Color
    val surfaceContainerHighest: Color
    val outline: Color
    val outlineVariant: Color
    val scrim: Color
    val inverseSurface: Color
    val inverseOnSurface: Color
    val inversePrimary: Color

    fun toColorScheme(): ColorScheme
}
