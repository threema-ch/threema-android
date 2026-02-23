package ch.threema.app.compose.theme.color

import androidx.compose.ui.graphics.Color

object ColorsLight : ComposeColorPaletteLight() {

    // Primary
    override val primary = Color(BrandColor.SHADE_800)
    override val onPrimary = Color(0xFFFFFFFF)
    override val primaryContainer = Color(BrandColor.SHADE_100)
    override val onPrimaryContainer = Color(0xFF1D1D1B) // Brand Text
    override val inversePrimary = Color(BrandColor.SHADE_600)

    // Secondary
    override val secondary = Color(BrandColor.SHADE_400)
    override val onSecondary = Color(BrandColor.SHADE_900)
    override val secondaryContainer = Color(BrandColor.SHADE_200)
    override val onSecondaryContainer = Color(BrandColor.SHADE_900)

    // Tertiary
    override val tertiary = Color(BrandColor.SHADE_100)
    override val onTertiary = Color(0xFF1D1D1B) // Brand Text
    override val tertiaryContainer = Color(BrandColor.SHADE_100)
    override val onTertiaryContainer = Color(0xFF1D1D1B) // Brand Text

    // Error
    override val error = Color(0xFFE11D24)
    override val onError = Color(0xFFFFFFFF)
    override val errorContainer = Color(0xFFFFDAD6)
    override val onErrorContainer = Color(0xFF410002)

    // Background/Surface
    override val background = Color(0xFFFEFFFE)
    override val onBackground = Color(0xFF1D1D1B) // Brand Text
    override val surface = Color(0xFFFEFFFE)
    override val onSurface = Color(0xFF1D1D1B) // Brand Text
    override val surfaceVariant = Color(0xFFEFEFEF)
    override val onSurfaceVariant = Color(0xFF1D1D1B) // Brand Text
    override val inverseSurface = Color(0xFF1D1D1B) // Brand Text
    override val inverseOnSurface = Color(0xFFFFFFFF)

    // Container
    override val surfaceContainerLowest = Color(0xFFFFFFFF)
    override val surfaceContainerLow = Color(BrandGreyColors.SHADE_50)
    override val surfaceContainer = Color(BrandGreyColors.SHADE_100)
    override val surfaceContainerHigh = Color(BrandGreyColors.SHADE_200)
    override val surfaceContainerHighest = Color(BrandGreyColors.SHADE_300)

    // Other
    override val outline = Color(BrandGreyColors.SHADE_600)
    override val outlineVariant = Color(BrandGreyColors.SHADE_600)
    override val scrim = Color(0xFF000000)
}
