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

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

@Composable
fun TextButtonPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    iconLeading: ButtonIconInfo? = null,
    iconTrailing: ButtonIconInfo? = null,
) {
    TextButtonBase(
        modifier = modifier.heightIn(min = GridUnit.x6),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        contentColor = MaterialTheme.colorScheme.primary,
        iconLeading = iconLeading,
        iconTrailing = iconTrailing,
    )
}

@Composable
fun TextButtonNeutral(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    iconLeading: ButtonIconInfo? = null,
    iconTrailing: ButtonIconInfo? = null,
) {
    TextButtonBase(
        modifier = modifier.heightIn(min = GridUnit.x6),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        contentColor = MaterialTheme.colorScheme.onSurface,
        iconLeading = iconLeading,
        iconTrailing = iconTrailing,
    )
}

@Composable
private fun TextButtonBase(
    modifier: Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    textStyle: TextStyle,
    contentColor: Color,
    iconLeading: ButtonIconInfo?,
    iconTrailing: ButtonIconInfo?,
) {
    TextButton(
        modifier = modifier,
        colors = ButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor.copy(
                alpha = AlphaValues.DISABLED_ON_CONTAINER,
            ),
        ),
        onClick = onClick,
        enabled = enabled,
    ) {
        if (iconLeading != null) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        24.sp.toDp()
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
                        24.sp.toDp()
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
@Composable
fun TextButtonPrimary_Preview() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun TextButtonPrimary_Preview_Night() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun TextButtonPrimary_Preview_Night_Disabled() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@Preview
@Composable
fun TextButtonPrimary_Preview_Trailing_Icon() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconTrailing = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Composable
fun TextButtonPrimary_Preview_Both_Icons() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
            iconTrailing = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Composable
fun TextButtonPrimary_Preview_Disabled() {
    ThreemaThemePreview {
        TextButtonPrimary(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@Preview
@Composable
fun TextButtonPrimary_Preview_FullWidth() {
    ThreemaThemePreview {
        TextButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Composable
fun TextButtonNeutral_Preview() {
    ThreemaThemePreview {
        TextButtonNeutral(
            onClick = {},
            text = "Sign In",
        )
    }
}

@Preview
@Composable
fun TextButtonNeutral_Preview_Disabled() {
    ThreemaThemePreview {
        TextButtonNeutral(
            onClick = {},
            text = "Sign In",
            enabled = false,
        )
    }
}

@Preview
@Composable
fun TextButtonNeutral_Preview_Leading_Icon() {
    ThreemaThemePreview {
        TextButtonNeutral(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun TextButtonNeutral_Preview_Night() {
    ThreemaThemePreview {
        TextButtonNeutral(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun TextButtonNeutral_Preview_Night_Disabled() {
    ThreemaThemePreview {
        TextButtonNeutral(
            onClick = {},
            text = "Sign In",
            iconLeading = ButtonIconInfo(
                icon = R.drawable.ic_arrow_upward,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}
