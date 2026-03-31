package ch.threema.app.compose.common.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit

private val containerColor: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

private val contentColor: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimary

@Composable
fun FloatingActionButtonPrimary(
    modifier: Modifier = Modifier,
    icon: ButtonIconInfo,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        FloatingActionButtonPrimaryIcon(icon)
    }
}

@Composable
fun ExtendedFloatingActionButtonPrimary(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    icon: ButtonIconInfo,
    text: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
) {
    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        expanded = expanded,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        icon = {
            FloatingActionButtonPrimaryIcon(icon)
        },
        text = {
            ThemedText(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun FloatingActionButtonPrimaryIcon(icon: ButtonIconInfo) {
    Icon(
        painter = painterResource(icon.icon),
        contentDescription = icon.contentDescription?.let { stringRes ->
            stringResource(stringRes)
        },
        tint = LocalContentColor.current,
    )
}

@Composable
@Preview(group = "light")
private fun Preview_FloatingActionPrimaryButton() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            FloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(group = "dark")
private fun Preview_FloatingActionPrimaryButton_Dark() {
    ThreemaThemePreview(
        isDarkTheme = true,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            FloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(group = "light")
private fun Preview_ExtendedFloatingActionButtonPrimary() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ExtendedFloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                text = "Start Chat",
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(group = "dark")
private fun Preview_ExtendedFloatingActionButtonPrimary_Dark() {
    ThreemaThemePreview(
        isDarkTheme = true,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ExtendedFloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                text = "Start Chat",
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(group = "light")
private fun Preview_ExtendedFloatingActionButtonPrimary_Long() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ExtendedFloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                text = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna",
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(group = "light")
private fun Preview_ExtendedFloatingActionButtonPrimary_Collapsed() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            ExtendedFloatingActionButtonPrimary(
                modifier = Modifier.padding(GridUnit.x2),
                expanded = false,
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_chat_bubble,
                    contentDescription = null,
                ),
                text = "Start chat",
                onClick = {},
            )
        }
    }
}
