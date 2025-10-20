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

package ch.threema.app.compose.theme.color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object CustomColors {

    /**
     *  When changing sth. here, also change the XML equivalent
     *  - `color/bubble_send_colorstatelist.xml`
     *  - `color-night/bubble_send_colorstatelist.xml`
     */
    val chatBubbleSendContainer: Color
        @Composable
        @ReadOnlyComposable
        get() =
            if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }

    /**
     *  When changing sth. here, also change XML equivalent
     *  - `color/bubble_send_text_colorstatelist.xml`
     *  - `color-night/bubble_send_text_colorstatelist.xml`
     */
    val chatBubbleSendOnContainer: Color
        @Composable
        @ReadOnlyComposable
        get() =
            if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

    /**
     *  When changing sth. here, also change XML equivalent
     *  - `color/bubble_receive_colorstatelist.xml`
     *  - `color-night/bubble_receive_colorstatelist.xml`
     */
    val chatBubbleReceiveContainer: Color
        @Composable
        @ReadOnlyComposable
        get() =
            if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }

    /**
     *  When changing sth. here, also change XML equivalent
     *  - `color/bubble_receive_text_colorstatelist.xml`
     *  - `color-night/bubble_receive_text_colorstatelist.xml`
     */
    val chatBubbleReceiveOnContainer: Color
        @Composable
        @ReadOnlyComposable
        get() =
            if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
}
