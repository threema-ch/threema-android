package ch.threema.app.compose.common.buttons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

@Composable
fun ButtonOutlined(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors().copy(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
                .copy(alpha = AlphaValues.DISABLED),
        ),
    ) {
        if (leadingIcon != null) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        24.sp.toDp()
                    },
                ),
                painter = painterResource(leadingIcon.icon),
                contentDescription = stringResourceOrNull(leadingIcon.contentDescription),
                tint = LocalContentColor.current,
            )
            SpacerHorizontal(GridUnit.x1_5)
        }

        ThemedText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview() {
    ThreemaThemePreview {
        ButtonOutlined(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Invite friends",
        )
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview_Leading_Icon() {
    ThreemaThemePreview {
        ButtonOutlined(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Invite friends",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_person_add_outline,
                contentDescription = null,
            ),
        )
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview_Leading_Icon_Night() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface {
            ButtonOutlined(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Invite friends",
                leadingIcon = ButtonIconInfo(
                    icon = R.drawable.ic_person_add_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview_Disabled() {
    ThreemaThemePreview {
        ButtonOutlined(
            modifier = Modifier.padding(GridUnit.x1),
            onClick = {},
            text = "Invite friends",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_person_add_outline,
                contentDescription = null,
            ),
            enabled = false,
        )
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview_Disabled_Night() {
    ThreemaThemePreview(isDarkTheme = true) {
        Surface {
            ButtonOutlined(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Invite friends",
                leadingIcon = ButtonIconInfo(
                    icon = R.drawable.ic_person_add_outline,
                    contentDescription = null,
                ),
                enabled = false,
            )
        }
    }
}

@Preview
@Composable
fun ButtonOutlined_Preview_FullWidth() {
    ThreemaThemePreview {
        ButtonOutlined(
            modifier = Modifier
                .padding(GridUnit.x1)
                .fillMaxWidth(),
            onClick = {},
            text = "Invite friends",
        )
    }
}
