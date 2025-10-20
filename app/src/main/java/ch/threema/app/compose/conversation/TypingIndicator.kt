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

package ch.threema.app.compose.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.app.compose.theme.dimens.GridUnit

private const val DOT_COUNT = 3

private const val ALPHA_DOT_MIN = 0.2f
private const val ALPHA_DOT_MAX = 1.0f

/**
 *  The total duration one dot takes to animate from [ALPHA_DOT_MIN] to [ALPHA_DOT_MAX]
 */
private const val DURATION_BLEND_IN_MS = 200

/**
 *  The total duration one dot takes to animate *back* from [ALPHA_DOT_MAX] to [ALPHA_DOT_MIN]
 */
private const val DURATION_BLEND_OUT_MS = 200

/**
 *  The next dot to the right begins its alpha animation `DURATION_OVERLAP_MS` milliseconds before the current dots animation is completed.
 *
 *  Should *never* be bigger than the sum of [DURATION_BLEND_IN_MS] and [DURATION_BLEND_OUT_MS].
 */
private const val DURATION_OVERLAP_MS = 200

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
) {
    val isPreview: Boolean = LocalInspectionMode.current
    var isVisible: Boolean by remember {
        // To make the previews work, we just directly set it to visible for previews
        mutableStateOf(isPreview)
    }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    AnimatedVisibility(
        modifier = modifier
            .height(24.dp)
            .wrapContentWidth(),
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 500,
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .clip(
                    shape = CircleShape.copy(
                        topStart = CornerSize(2.dp),
                    ),
                )
                .background(
                    color = CustomColors.chatBubbleReceiveContainer,
                )
                .padding(
                    horizontal = GridUnit.x1,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(DOT_COUNT) { index ->
                Dot(
                    modifier = Modifier.padding(horizontal = GridUnit.x0_25),
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun Dot(
    modifier: Modifier,
    index: Int,
) {
    val durationBlendInOutMs: Int = DURATION_BLEND_IN_MS + DURATION_BLEND_OUT_MS

    // Add a start delay depending on the position of the dot in the row and the defined overlap duration
    val durationWaitBeforeStartMs: Int = (index * durationBlendInOutMs) - (index * DURATION_OVERLAP_MS)

    // Add a delay after the own alpha transition is completed, to not repeat the own animation too early
    val durationWaitAfterEndMs: Int = (
        (
            DOT_COUNT * durationBlendInOutMs
            ) -
            ((index + 1) * durationBlendInOutMs) -
            (DURATION_OVERLAP_MS * (DOT_COUNT - index - 1))
        )

    val totalDuration: Int = durationWaitBeforeStartMs + durationBlendInOutMs + durationWaitAfterEndMs

    val infiniteTransition: InfiniteTransition = rememberInfiniteTransition()
    val alphaValue: Float by infiniteTransition.animateFloat(
        initialValue = ALPHA_DOT_MIN,
        targetValue = ALPHA_DOT_MIN,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = totalDuration
                ALPHA_DOT_MIN at 0
                ALPHA_DOT_MIN at durationWaitBeforeStartMs
                ALPHA_DOT_MAX at durationWaitBeforeStartMs + DURATION_BLEND_IN_MS
                ALPHA_DOT_MIN at durationWaitBeforeStartMs + DURATION_BLEND_IN_MS + DURATION_BLEND_OUT_MS
                ALPHA_DOT_MIN at totalDuration
            },
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = modifier
            .size(4.dp)
            .graphicsLayer(
                alpha = alphaValue,
            )
            .clip(CircleShape)
            .background(
                color = MaterialTheme.colorScheme.onSurface,
            ),
    )
}

@PreviewLightDark
@Composable
private fun TypingIndicator_Preview() {
    ThreemaThemePreview {
        Box(
            modifier = Modifier
                .size(
                    width = 100.dp,
                    height = 50.dp,
                )
                .padding(GridUnit.x1),
            contentAlignment = Alignment.CenterStart,
        ) {
            TypingIndicator()
        }
    }
}
