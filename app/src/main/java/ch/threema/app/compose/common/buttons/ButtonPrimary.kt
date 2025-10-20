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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

@Composable
fun ButtonPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    iconLeading: ButtonIconInfo? = null,
    iconTrailing: ButtonIconInfo? = null,
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
        iconLeading = iconLeading,
        iconTrailing = iconTrailing,
    )
}

@Composable
fun ButtonPrimaryDense(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    iconLeading: ButtonIconInfo? = null,
    iconTrailing: ButtonIconInfo? = null,
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
        iconLeading = iconLeading,
        iconTrailing = iconTrailing,
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
    iconLeading: ButtonIconInfo? = null,
    iconTrailing: ButtonIconInfo? = null,
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
        iconLeading = iconLeading,
        iconTrailing = iconTrailing,
    )
}

/**
 *  @param iconSize Is defined as `sp` ([TextUnit]) to grow and shrink uniformly with system font zoom
 */
@Composable
private fun ButtonPrimaryBase(
    modifier: Modifier,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    textStyle: TextStyle,
    iconSize: TextUnit,
    shape: Shape,
    iconLeading: ButtonIconInfo?,
    iconTrailing: ButtonIconInfo?,
) {
    Button(
        modifier = modifier,
        colors = ButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(
                alpha = AlphaValues.DISABLED_CONTAINER,
            ),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(
                alpha = AlphaValues.DISABLED_ON_CONTAINER,
            ),
        ),
        shape = shape,
        contentPadding = contentPadding,
        onClick = onClick,
        enabled = enabled,
    ) {
        if (iconLeading != null) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        iconSize.toDp()
                    },
                ),
                painter = painterResource(iconLeading.icon),
                contentDescription = stringResourceOrNull(iconLeading.contentDescription),
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

        if (iconTrailing != null) {
            Spacer(Modifier.width(GridUnit.x1_5))
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        iconSize.toDp()
                    },
                ),
                painter = painterResource(iconTrailing.icon),
                contentDescription = stringResourceOrNull(iconTrailing.contentDescription),
                tint = LocalContentColor.current,
            )
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_Leading_Icon() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_Trailing_Icon() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconTrailing = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_Both_Icons() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
            iconTrailing = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_Disabled() {
    ThreemaThemePreview {
        ButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_language_outline,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimaryDense_Preview() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimaryDense_Preview_Leading_Icon() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            onClick = {},
            text = "Preview",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview(fontScale = 2.0f)
@Preview(fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimary_Preview_Zoom() {
    ThreemaThemePreview {
        ButtonPrimaryDense(
            onClick = {},
            text = "Preview",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview()
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimaryWebsite_Preview() {
    ThreemaThemePreview {
        ButtonPrimaryWebsite(
            onClick = {},
            text = "Preview",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}

@Preview()
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPrimaryWebsite_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonPrimaryWebsite(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            text = "Preview",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_new_feature,
                contentDescription = null,
            ),
        )
    }
}
