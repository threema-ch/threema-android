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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
            modifier = Modifier.alpha(
                if (enabled) 1f else .4f,
            ),
            text = text,
            style = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun ButtonPrimary_Preview() {
    ThreemaThemePreview {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            ButtonPrimary(
                modifier = Modifier.padding(paddingValues),
                onClick = {},
                text = "Preview",
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun ButtonPrimary_Preview_Disabled() {
    ThreemaThemePreview {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            ButtonPrimary(
                modifier = Modifier.padding(paddingValues),
                onClick = {},
                text = "Preview",
                enabled = false,
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun ButtonPrimary_Preview_FullWidth() {
    ThreemaThemePreview {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            ButtonPrimary(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                onClick = {},
                text = "Preview",
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun ButtonPrimarySmall_Preview() {
    ThreemaThemePreview {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            ButtonPrimarySmall(
                modifier = Modifier.padding(paddingValues),
                onClick = {},
                text = "Preview",
            )
        }
    }
}
