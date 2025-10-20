/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.compose.theme.color

import androidx.compose.ui.graphics.Color

object ColorsDark : ComposeColorPaletteDark() {

    // Primary
    override val primary = Color(BrandColor.SHADE_500)
    override val onPrimary = Color(0xFF000000)
    override val primaryContainer = Color(BrandColor.SHADE_500)
    override val onPrimaryContainer = Color(0xFF000000)
    override val inversePrimary = Color(BrandColor.SHADE_700)

    // Secondary
    override val secondary = Color(BrandColor.SHADE_400)
    override val onSecondary = Color(0xFF000000)
    override val secondaryContainer = Color(BrandColor.SHADE_800)
    override val onSecondaryContainer = Color(0xFFF3F4F6) // Brand Text

    // Tertiary
    override val tertiary = Color(BrandGreyColors.SHADE_800)
    override val onTertiary = Color(0xFFF3F4F6) // Brand Text
    override val tertiaryContainer = Color(BrandGreyColors.SHADE_800)
    override val onTertiaryContainer = Color(0xFFF3F4F6) // Brand Text

    // Error
    override val error = Color(0xFFEDA19D)
    override val onError = Color(0xFF000000)
    override val errorContainer = Color(0xFF93000A)
    override val onErrorContainer = Color(0xFFFFDAD6)

    // Background/Surface
    override val background = Color(0xFF000000)
    override val onBackground = Color(0xFFF3F4F6) // Brand Text
    override val surface = Color(0xFF000000)
    override val onSurface = Color(0xFFF3F4F6) // Brand Text
    override val surfaceVariant = Color(0xFF000000)
    override val onSurfaceVariant = Color(0xFFF3F4F6) // Brand Text
    override val inverseSurface = Color(0xFFF3F4F6) // Brand Text
    override val inverseOnSurface = Color(0xFF000000)

    // Container
    override val surfaceContainerLowest = Color(0xFF000000)
    override val surfaceContainerLow = Color(BrandGreyColors.SHADE_900)
    override val surfaceContainer = Color(BrandGreyColors.SHADE_800)
    override val surfaceContainerHigh = Color(BrandGreyColors.SHADE_700)
    override val surfaceContainerHighest = Color(BrandGreyColors.SHADE_600)

    // Other
    override val outline = Color(BrandGreyColors.SHADE_100)
    override val outlineVariant = Color(BrandGreyColors.SHADE_50)
    override val scrim = Color(0xFF000000)
}
