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

package ch.threema.app.activities.wizard.components

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit

private const val BORDER_WIDTH = 1

enum class WizardButtonStyle {
    DEFAULT,
    INVERSE,
}

/**
 *  This button represents the button used in all wizard screens and dialogs.
 *
 *  The wizard has fixed dark theme.
 *
 *  We do **not** use the dynamic theme colors from [MaterialTheme.colorScheme] because we do not want
 *  Material You to take effect in this composable.
 */
@Composable
fun WizardButton(
    modifier: Modifier = Modifier,
    text: String,
    @DrawableRes trailingIconRes: Int? = null,
    style: WizardButtonStyle = WizardButtonStyle.DEFAULT,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    var showAsInverse: Boolean by remember {
        mutableStateOf(style == WizardButtonStyle.INVERSE)
    }

    @ColorRes
    val containerColorRes: Int by remember {
        derivedStateOf {
            when (showAsInverse) {
                true -> android.R.color.transparent
                false -> R.color.md_theme_dark_primary
            }
        }
    }

    @ColorRes
    val contentColorRes: Int by remember {
        derivedStateOf {
            when (showAsInverse) {
                true -> R.color.md_theme_dark_primary
                false -> R.color.md_theme_dark_onPrimary
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> showAsInverse = !showAsInverse
                is PressInteraction.Release -> showAsInverse = !showAsInverse
                is PressInteraction.Cancel -> showAsInverse = style == WizardButtonStyle.INVERSE
            }
        }
    }

    OutlinedButton(
        modifier = Modifier
            .heightIn(min = dimensionResource(R.dimen.wizard_button_height))
            .then(modifier),
        interactionSource = interactionSource,
        onClick = onClick,
        enabled = isEnabled,
        colors = buildWizardButtonColors(
            containerColorRes = containerColorRes,
            contentColorRes = contentColorRes,
        ),
        border = buildBorderStroke(
            isEnabled = isEnabled,
            showAsInverse = showAsInverse,
        ),
        shape = RoundedCornerShape(size = 4.dp),
        contentPadding = PaddingValues(
            horizontal = GridUnit.x2,
            vertical = GridUnit.x0_5,
        ),
    ) {
        ThemedText(
            modifier = Modifier.weight(
                weight = 1f,
                fill = false,
            ),
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
            ),
            color = colorResource(contentColorRes).copy(
                alpha = if (isEnabled) 1f else AlphaValues.DISABLED_ON_CONTAINER,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (trailingIconRes != null) {
            Spacer(Modifier.width(GridUnit.x1_5))
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(id = trailingIconRes),
                contentDescription = null,
                tint = colorResource(contentColorRes).copy(
                    alpha = if (isEnabled) 1f else AlphaValues.DISABLED_ON_CONTAINER,
                ),
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun buildBorderStroke(
    isEnabled: Boolean,
    showAsInverse: Boolean,
): BorderStroke? = when {
    isEnabled && showAsInverse -> BorderStroke(
        width = BORDER_WIDTH.dp,
        color = colorResource(R.color.md_theme_dark_primary),
    )

    !isEnabled -> BorderStroke(
        width = BORDER_WIDTH.dp,
        color = colorResource(R.color.md_theme_dark_primary).copy(
            alpha = AlphaValues.DISABLED_CONTAINER,
        ),
    )

    else -> null
}

@Composable
@ReadOnlyComposable
private fun buildWizardButtonColors(
    @ColorRes containerColorRes: Int,
    @ColorRes contentColorRes: Int,
): ButtonColors {
    val containerColor = colorResource(containerColorRes)
    val contentColor = colorResource(contentColorRes)
    return ButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = containerColor.copy(
            alpha = AlphaValues.DISABLED_CONTAINER,
        ),
        disabledContentColor = contentColor.copy(
            alpha = AlphaValues.DISABLED_ON_CONTAINER,
        ),
    )
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewEnabled() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                onClick = {},
            )
        }
    }
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewEnabledLongText() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "A new version is available. Would you like to download it now?",
                onClick = {},
            )
        }
    }
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewDisabled() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                isEnabled = false,
                onClick = {},
            )
        }
    }
}

@Preview(group = "inverse")
@Composable
private fun WizardButtonPreviewEnabledInverse() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                style = WizardButtonStyle.INVERSE,
                onClick = {},
            )
        }
    }
}

@Preview(group = "inverse")
@Composable
private fun WizardButtonPreviewDisabledInverse() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                style = WizardButtonStyle.INVERSE,
                isEnabled = false,
                onClick = {},
            )
        }
    }
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewIconLongText() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Verifying your mobile number failed. Please make sure the number you entered is " +
                    "correct and you are connected to your mobile network before trying again.",
                trailingIconRes = R.drawable.ic_new_feature,
                onClick = {},
            )
        }
    }
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewIcon() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                trailingIconRes = R.drawable.ic_new_feature,
                onClick = {},
            )
        }
    }
}

@Preview(group = "inverse")
@Composable
private fun WizardButtonPreviewIconInverse() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                trailingIconRes = R.drawable.ic_new_feature,
                style = WizardButtonStyle.INVERSE,
                onClick = {},
            )
        }
    }
}

@Preview(group = "default")
@Composable
private fun WizardButtonPreviewIconDisabled() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                trailingIconRes = R.drawable.ic_new_feature,
                isEnabled = false,
                onClick = {},
            )
        }
    }
}

@Preview(group = "inverse")
@Composable
private fun WizardButtonPreviewIconDisabledInverse() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface(color = Color.Black) {
            WizardButton(
                modifier = Modifier.padding(8.dp),
                text = "Close",
                trailingIconRes = R.drawable.ic_new_feature,
                style = WizardButtonStyle.INVERSE,
                isEnabled = false,
                onClick = {},
            )
        }
    }
}
