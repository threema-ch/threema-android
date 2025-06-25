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

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import ch.threema.app.R
import ch.threema.app.compose.common.AvatarAsync
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.services.AvatarCacheService
import ch.threema.storage.models.ReceiverModel

private const val FLIP_ANIMATION_DURATION_MILLIS = 400

/**
 *  Loads the avatar of the passed [receiverModel] from the local disk asynchronously.
 *  In the meantime, or if no avatar exists, the [fallbackIcon] will be displayed.
 *
 *  Animates with a horizontal card-flip animation between the two [isChecked] states.
 */
@Composable
fun AvatarAsyncCheckable(
    modifier: Modifier = Modifier,
    avatarCacheService: AvatarCacheService,
    receiverModel: ReceiverModel,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int,
    showWorkBadge: Boolean,
    isChecked: Boolean,
) {
    val rotation = animateFloatAsState(
        targetValue = when (isChecked) {
            true -> 180f
            false -> 0f
        },
        animationSpec = tween(
            durationMillis = FLIP_ANIMATION_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )

    Box(
        modifier = modifier
            .size(GridUnit.x5)
            .graphicsLayer {
                rotationY = rotation.value
            },
    ) {
        if (rotation.value <= 90f) {
            AvatarAsync(
                modifier = Modifier.fillMaxSize(),
                avatarCacheService = avatarCacheService,
                receiverModel = receiverModel,
                contentDescription = contentDescription,
                fallbackIcon = fallbackIcon,
                showWorkBadge = showWorkBadge,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = 180f
                    },
            ) {
                CheckedIndicator(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CheckedIndicator(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(color = MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(GridUnit.x3),
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
