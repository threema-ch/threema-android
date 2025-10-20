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

package ch.threema.app.compose.common.list.swipe

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.responsive

/**
 *  Container used for display in the [SwipeToDismissBox]s `backgroundContent`. Note that this composable stretches itself to the maximum available
 *  bounds. So it has to be constrained from the outside.
 *
 *  @param containerColorSettled The start color used for the color transition
 *  @param swipeProgress The progress percentage of the horizontal drag compared to the total width of the list view. Starting from `0.0` ending
 *  at `1.0`.
 */
@Composable
fun <T> ListItemSwipeContainer(
    swipeFeature: ListItemSwipeFeature<T>,
    containerColorSettled: Color,
    shape: Shape,
    swipeProgress: Float,
) {
    val fractionColorLerp: Float = (swipeProgress + 0.4f).coerceAtMost(1f)

    // Used to animate the spacing from the icon to the edge of the container
    val fractionSpacingStart: Float = when (swipeFeature.direction) {
        SwipeDirection.StartToEnd -> (swipeProgress + 0.4f).coerceAtMost(1f)
        SwipeDirection.EndToStart -> 1f
    }
    val fractionSpacingEnd: Float = when (swipeFeature.direction) {
        SwipeDirection.StartToEnd -> 1f
        SwipeDirection.EndToStart -> (swipeProgress + 0.4f).coerceAtMost(1f)
    }

    // Used to animate the spacing between the text and the icon
    val fractionSpacingMiddle: Float = (swipeProgress + 0.3f).coerceAtMost(1f)

    val contentIcon: @Composable RowScope.() -> Unit = {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(swipeFeature.state.icon),
            contentDescription = null,
        )
    }

    val contentText: @Composable RowScope.() -> Unit = {
        ThemedText(
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            text = swipeFeature.state.text,
            color = LocalContentColor.current,
            textAlign = when (swipeFeature.direction) {
                SwipeDirection.StartToEnd -> TextAlign.Start
                SwipeDirection.EndToStart -> TextAlign.End
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    CompositionLocalProvider(LocalContentColor provides swipeFeature.contentColor) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    shape = shape,
                )
                .drawBehind {
                    drawRect(
                        color = lerp(
                            start = containerColorSettled,
                            stop = swipeFeature.containerColor,
                            fraction = fractionColorLerp,
                        ),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpacerHorizontal(
                width = GridUnit.x2.responsive * fractionSpacingStart,
            )
            when (swipeFeature.direction) {
                SwipeDirection.StartToEnd -> contentIcon()
                SwipeDirection.EndToStart -> contentText()
            }
            SpacerHorizontal(
                width = GridUnit.x2.responsive * fractionSpacingMiddle,
            )
            when (swipeFeature.direction) {
                SwipeDirection.StartToEnd -> contentText()
                SwipeDirection.EndToStart -> contentIcon()
            }
            SpacerHorizontal(
                width = GridUnit.x2.responsive * fractionSpacingEnd,
            )
        }
    }
}

@Composable
@PreviewSwipeableActionContainer
private fun Preview_SwipeableActionContainer_Progress_StartToEnd(
    @PreviewParameter(PreviewParameterProviderSwipeProgress::class) swipeProgress: Float,
) {
    ThreemaThemePreview {
        ListItemSwipeContainer(
            swipeFeature = ListItemSwipeFeature.StartToEnd<ConversationUiModel>(
                onSwipe = {},
                containerColor = Color.Red,
                contentColor = Color.White,
                state = ListItemSwipeFeatureState(
                    icon = R.drawable.ic_pin,
                    text = "Pin",
                ),
            ),
            containerColorSettled = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(GridUnit.x1),
            swipeProgress = swipeProgress,
        )
    }
}

@Composable
@PreviewSwipeableActionContainer
private fun Preview_SwipeableActionContainer_Progress_EndToStart(
    @PreviewParameter(PreviewParameterProviderSwipeProgress::class) swipeProgress: Float,
) {
    ThreemaThemePreview {
        ListItemSwipeContainer(
            swipeFeature = ListItemSwipeFeature.EndToStart<ConversationUiModel>(
                onSwipe = {},
                containerColor = Color.Blue,
                contentColor = Color.White,
                state = ListItemSwipeFeatureState(
                    icon = R.drawable.ic_archive_outline,
                    text = "Archive",
                ),
            ),
            containerColorSettled = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(GridUnit.x1),
            swipeProgress = swipeProgress,
        )
    }
}

@Composable
@PreviewSwipeableActionContainer
private fun Preview_SwipeableActionContainer_LongText() {
    ThreemaThemePreview {
        ListItemSwipeContainer(
            swipeFeature = ListItemSwipeFeature.StartToEnd<ConversationUiModel>(
                onSwipe = {},
                containerColor = Color.Red,
                contentColor = Color.White,
                state = ListItemSwipeFeatureState(
                    icon = R.drawable.ic_pin,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus.",
                ),
            ),
            containerColorSettled = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(GridUnit.x1),
            swipeProgress = 1.0f,
        )
    }
}

@Preview(heightDp = 60, widthDp = 300)
annotation class PreviewSwipeableActionContainer

private class PreviewParameterProviderSwipeProgress : PreviewParameterProvider<Float> {
    override val values: Sequence<Float> = (0..10).map { it * 0.1f }.asSequence()
}
