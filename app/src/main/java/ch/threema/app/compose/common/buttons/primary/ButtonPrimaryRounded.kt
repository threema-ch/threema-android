package ch.threema.app.compose.common.buttons.primary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

private val minSize = GridUnit.x5

@Composable
fun ButtonPrimaryRounded(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ButtonIconInfo? = null,
) {
    var buttonHeight: Dp by remember { mutableStateOf(minSize) }
    val colorContainer = if (enabled || isLoading) primaryButtonColors.containerColor else primaryButtonColors.disabledContainerColor
    val colorContent = if (enabled || isLoading) primaryButtonColors.contentColor else primaryButtonColors.disabledContentColor

    Box(
        modifier = modifier
            .sizeIn(
                minHeight = minSize,
                minWidth = minSize,
            )
            .clip(
                shape = CircleShape,
            )
            .background(
                color = colorContainer,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colorContent) {
            AnimatedContent(
                targetState = isLoading,
                contentAlignment = Alignment.Center,
            ) { isLoading ->
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.height(buttonHeight),
                    )
                } else {
                    val density = LocalDensity.current
                    ButtonPrimaryRoundedBase(
                        modifier = Modifier.onSizeChanged { size ->
                            buttonHeight = with(density) { size.height.toDp() }
                        },
                        text = text,
                        icon = icon,
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonPrimaryRoundedBase(
    modifier: Modifier,
    text: String,
    icon: ButtonIconInfo?,
) {
    Row(
        modifier = modifier
            .padding(
                paddingValues = ButtonDefaults.ContentPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(icon.icon),
                contentDescription = stringResourceOrNull(icon.contentDescription),
                tint = LocalContentColor.current,
            )
            SpacerHorizontal(GridUnit.x1)
        }

        ThemedText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingIndicator(
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(GridUnit.x3),
            strokeWidth = 3.dp,
            color = LocalContentColor.current,
        )
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_Loading() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                isLoading = true,
                text = "Sign In",
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_Leading_Icon() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_Leading_Icon_Loading() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                isLoading = true,
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_Disabled() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                enabled = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_Disabled_Loading() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                enabled = false,
                isLoading = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_FullWidth() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier
                    .padding(GridUnit.x1)
                    .fillMaxWidth(),
                onClick = {},
                text = "Sign In",
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ButtonPrimaryRounded_Preview_FullWidth_Loading() {
    ThreemaThemePreview {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier
                    .padding(GridUnit.x1)
                    .fillMaxWidth(),
                onClick = {},
                text = "Sign In",
                isLoading = true,
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors_Loading() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                isLoading = true,
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors_Disabled() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                enabled = false,
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors_Disabled_Loading() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                enabled = false,
                isLoading = true,
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors_Dark() {
    ThreemaThemePreview(
        isDarkTheme = true,
        shouldUseDynamicColors = true,
    ) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
            )
        }
    }
}

@PreviewDynamicColors
@Composable
fun ButtonPrimaryRounded_Preview_DynamicColors_Dark_Loading() {
    ThreemaThemePreview(
        isDarkTheme = true,
        shouldUseDynamicColors = true,
    ) {
        Surface {
            ButtonPrimaryRounded(
                modifier = Modifier.padding(GridUnit.x1),
                onClick = {},
                text = "Sign In",
                icon = ButtonIconInfo(
                    icon = R.drawable.ic_language_outline,
                    contentDescription = null,
                ),
                isLoading = true,
            )
        }
    }
}
