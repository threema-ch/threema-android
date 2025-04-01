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

object ColorsLight : ComposeColorPaletteLight() {
    override val primary = Color(0xFF005FAD)
    override val onPrimary = Color(0xFFFFFFFF)
    override val primaryContainer = Color(0xFFD4E3FF)
    override val onPrimaryContainer = Color(0xFF001C3A)
    override val secondary = Color(0xFF545F71)
    override val onSecondary = Color(0xFFFFFFFF)
    override val secondaryContainer = Color(0xFFE9F5FE)
    override val onSecondaryContainer = Color(0xFF111C2B)
    override val tertiary = Color(0xFF6D5676)
    override val onTertiary = Color(0xFFFFFFFF)
    override val tertiaryContainer = Color(0xFFF6D9FF)
    override val onTertiaryContainer = Color(0xFF271430)
    override val error = Color(0xFFBA1A1A)
    override val onError = Color(0xFFFFFFFF)
    override val errorContainer = Color(0xFFFFDAD6)
    override val onErrorContainer = Color(0xFF410002)
    override val background = Color(0xFFFDFCFF)
    override val onBackground = Color(0xFF1A1C1E)
    override val surface = Color(0xFFFDFCFF)
    override val onSurface = Color(0xFF1A1C1E)
    override val surfaceVariant = Color(0xFFE2EBF6)
    override val onSurfaceVariant = Color(0xFF43474E)
    override val outline = Color(0xFF73777F)
    override val outlineVariant = Color(0xFFC3C6CF)
    override val scrim = Color(0xFF000000)
    override val inverseSurface = Color(0xFF2F3033)
    override val inverseOnSurface = Color(0xFFF1F0F4)
    override val inversePrimary = Color(0xFFA4C8FF)
}
