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

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

private val primaryButtonColors: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(
            alpha = AlphaValues.DISABLED_CONTAINER,
        ),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(
            alpha = AlphaValues.DISABLED_ON_CONTAINER,
        ),
    )

@Composable
fun ButtonPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
    trailingIcon: ButtonIconInfo? = null,
) {
    ButtonPrimaryBase(
        modifier = modifier.heightIn(GridUnit.x6),
        contentPadding = PaddingValues(
            horizontal = GridUnit.x3,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        iconSize = 24.sp,
        shape = ShapeDefaults.Medium,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/**
 *  A version of the [ButtonPrimary] that looks and behaves exactly the same, but allows to specify the button colors with [colorPrimaryOverride] and
 *  [colorOnPrimaryOverride]. Usually you don't want to set these colors explicitly, as the whole [ButtonPrimary] component is designed to always
 *  use the implicit **primary** colors from the theme (hence the components name).
 *
 *  Note that when setting [colorPrimaryOverride] and [colorOnPrimaryOverride] to non-theme colors, *dynamic colors will not apply* to this component
 *  if enabled. Passing a combination of theme- and non-theme colors is **not recommended**, as this might break in some dynamic color palettes.
 */
@Composable
fun ButtonPrimaryOverride(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    colorPrimaryOverride: Color,
    colorOnPrimaryOverride: Color,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
    trailingIcon: ButtonIconInfo? = null,
) {
    ButtonPrimaryBase(
        modifier = modifier.heightIn(GridUnit.x6),
        colors = ButtonColors(
            containerColor = colorPrimaryOverride,
            contentColor = colorOnPrimaryOverride,
            disabledContainerColor = colorPrimaryOverride.copy(
                alpha = AlphaValues.DISABLED_CONTAINER,
            ),
            disabledContentColor = colorOnPrimaryOverride.copy(
                alpha = AlphaValues.DISABLED_ON_CONTAINER,
            ),
        ),
        contentPadding = PaddingValues(
            horizontal = GridUnit.x3,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        iconSize = 24.sp,
        shape = ShapeDefaults.Medium,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun ButtonPrimaryDense(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
    trailingIcon: ButtonIconInfo? = null,
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
        textStyle = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        iconSize = 18.sp,
        shape = ShapeDefaults.Medium,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/**
 *  This is a special form of the [ButtonPrimary] that should not really be used inside of the app.
 *  It should only be used on special advertisement screens that are designwise closer to our website.
 */
@Composable
fun ButtonPrimaryWebsite(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
    trailingIcon: ButtonIconInfo? = null,
) {
    ButtonPrimaryBase(
        modifier = modifier.heightIn(GridUnit.x6),
        contentPadding = PaddingValues(
            horizontal = GridUnit.x3,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        iconSize = 24.sp,
        shape = RoundedCornerShape(
            percent = 50,
        ),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/**
 *  @param iconSize Is defined as `sp` ([TextUnit]) to grow and shrink uniformly with system font zoom
 */
@Composable
private fun ButtonPrimaryBase(
    modifier: Modifier,
    colors: ButtonColors = primaryButtonColors,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    textStyle: TextStyle,
    iconSize: TextUnit,
    shape: Shape,
    leadingIcon: ButtonIconInfo?,
    trailingIcon: ButtonIconInfo?,
) {
    Button(
        modifier = modifier,
        colors = colors,
        shape = shape,
        contentPadding = contentPadding,
        onClick = onClick,
        enabled = enabled,
    ) {
        if (leadingIcon != null) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        iconSize.toDp()
                    },
                ),
                painter = painterResource(leadingIcon.icon),
                contentDescription = stringResourceOrNull(leadingIcon.contentDescription),
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(GridUnit.x1_5))
        }

        ThemedText(
            text = text,
            style = textStyle,
            color = LocalContentColor.current,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (trailingIcon != null) {
            Spacer(Modifier.width(GridUnit.x1_5))
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        iconSize.toDp()
                    },
                ),
                painter = painterResource(trailingIcon.icon),
                contentDescription = stringResourceOrNull(trailingIcon.contentDescription),
                tint = LocalContentColor.current,
            )
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview(name = "Light - Icon Leading")
@Preview(name = "Dark - Icon Leading", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_Leading_Icon() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Icon Trailing")
@Preview(name = "Dark - Icon Trailing", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_Trailing_Icon() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Icon Both")
@Preview(name = "Dark - Icon Both", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_Both_Icons() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Disabled")
@Preview(name = "Dark - Disabled", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_Disabled() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@Preview(name = "Light - Expanded")
@Preview(name = "Dark - Expanded", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier
                .padding(GridUnit.x1)
                .fillMaxWidth(),
            onClick = {},
            text = "Sign In",
        )
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimary_Preview_DynamicColors() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimary_Preview_DynamicColors_Disabled() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        ButtonPrimary(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimary_Preview_DynamicColors_Dark() {
    ThreemaThemePreview(
        isDarkTheme = true,
        shouldUseDynamicColors = true,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ButtonPrimary(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                leadingIcon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryDense_Preview() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview(name = "Light - Icon")
@Preview(name = "Dark - Icon", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryDense_Preview_Leading_Icon() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Scaled Up", fontScale = 2.0f)
@Preview(name = "Dark - Scaled Up", fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimary_Preview_Zoom() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryWebsite_Preview() {
    ThreemaThemePreview {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Expanded")
@Preview(name = "Dark - Expanded", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryWebsite_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonPrimaryWebsite(
            modifier = Modifier
                .padding(GridUnit.x1)
                .fillMaxWidth(),
            onClick = {},
            text = "Preview",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryWebsite_Preview_DynamicColors() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark (ignoring)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryOverride_Preview() {
    ThreemaThemePreview {
        ButtonPrimaryOverride(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            colorPrimaryOverride = Color.Magenta,
            colorOnPrimaryOverride = Color.Cyan,
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(name = "Light - Disabled")
@Preview(name = "Dark (ignoring) - Disabled", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ButtonPrimaryOverride_Preview_Disabled() {
    ThreemaThemePreview {
        ButtonPrimaryOverride(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            colorPrimaryOverride = Color.Magenta,
            colorOnPrimaryOverride = Color.Cyan,
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryOverride_Preview_IgnoringDynamicColors() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        ButtonPrimaryOverride(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Preview",
            colorPrimaryOverride = Color.Magenta,
            colorOnPrimaryOverride = Color.Cyan,
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}
