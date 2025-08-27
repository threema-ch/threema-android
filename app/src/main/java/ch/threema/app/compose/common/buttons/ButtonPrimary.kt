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

package ch.threema.app.compose.common.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun ButtonPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    ButtonPrimaryBase(
        modifier = modifier.heightIn(min = GridUnit.x6),
        contentPadding = ButtonDefaults.ContentPadding,
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun ButtonPrimarySmall(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    ButtonPrimaryBase(
        modifier = modifier,
        contentPadding = PaddingValues(
            vertical = GridUnit.x0,
            horizontal = GridUnit.x1_5,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun ButtonPrimaryBase(
    modifier: Modifier,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    textStyle: TextStyle,
) {
    Button(
        modifier = modifier,
        colors = ButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaValues.DISABLED_CONTAINER),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = AlphaValues.DISABLED_ON_CONTAINER),
        ),
        shape = RoundedCornerShape(GridUnit.x1),
        contentPadding = contentPadding,
        onClick = onClick,
        enabled = enabled,
    ) {
        ThemedText(
            color = LocalContentColor.current,
            text = text,
            style = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
fun ButtonPrimary_Preview_LongText() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            maxLines = 2,
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec sapien magna, efficitur a urna efficitur, varius " +
                "fermentum arcu. Donec rhoncus erat sem, vel ultrices sapien pharetra sit amet.",
        )
    }
}

@Preview
@Composable
fun ButtonPrimary_Preview() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Preview",
        )
    }
}

@Preview
@Composable
fun ButtonPrimary_Preview_Disabled() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Preview",
            enabled = false,
        )
    }
}

@Preview
@Composable
fun ButtonPrimary_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            text = "Preview",
        )
    }
}

@Preview
@Composable
fun ButtonPrimarySmall_Preview() {
    ThreemaThemePreview {
        ButtonPrimarySmall(
            onClick = {},
            text = "Preview",
        )
    }
}
