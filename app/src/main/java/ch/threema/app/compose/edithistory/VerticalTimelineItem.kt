/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.compose.edithistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.AppTypography

@Composable
fun VerticalTimelineItem(
    modifier: Modifier = Modifier,
    color: Color,
    dotSize: Dp = 12.dp,
    lineWidth: Dp = 4.dp,
    shouldFadeOutLineBottom: Boolean = false,
    shouldFadeOutLineTop: Boolean = false,
    labelContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    fun getLineBackgroundWithBottomFade(): Modifier {
        return Modifier.background(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    .5f to color,
                    1f to Color.Transparent
                ),
            )
        )
    }

    fun getLineBackgroundWithTopFade(): Modifier {
        return Modifier.background(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.2f to Color.Transparent,
                    1f to color
                ),
            )
        )
    }

    Column(modifier) {
        val timelineMargin = 12.dp
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(lineWidth)
                        .then(
                            if (shouldFadeOutLineTop) getLineBackgroundWithTopFade() else Modifier.background(
                                color
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.width(timelineMargin))
            labelContent?.invoke()
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(dotSize), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(lineWidth)
                        .then(
                            if (shouldFadeOutLineBottom) getLineBackgroundWithBottomFade() else Modifier.background(
                                color
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.width(timelineMargin))
            Box(modifier = Modifier.padding(top = 4.dp)) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun VerticalTimelineItemPreview() {
    VerticalTimelineItem(
        color = MaterialTheme.colorScheme.secondaryContainer,
        dotSize = 12.dp,
        lineWidth = 4.dp,
        shouldFadeOutLineBottom = true,
        shouldFadeOutLineTop = true,
        labelContent = {
            ThemedText(
                modifier = Modifier.padding(start = 4.dp),
                text = "Label",
                style = AppTypography.labelLarge,
                color = Color.White
            )
        }
    ) {
        Box(
            modifier = Modifier
                .height(100.dp)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
        )
    }
}
