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

package ch.threema.app.compose.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.glide.AvatarOptions
import ch.threema.app.services.AvatarCacheService
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.ReceiverModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AvatarAsync(
    modifier: Modifier = Modifier,
    receiverModel: ReceiverModel,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int,
    showWorkBadge: Boolean,
) {
    // Fallback composable for compose previews
    if (LocalInspectionMode.current) {
        PreviewFallback(receiverModel, showWorkBadge)
        return
    }

    val avatarCacheService = koinInject<AvatarCacheService>()

    var avatarBitmap: ImageBitmap? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        launch {
            avatarBitmap = when (receiverModel) {
                is ContactModel -> avatarCacheService.getIdentityAvatar(receiverModel.identity, AvatarOptions.PRESET_DEFAULT_FALLBACK)
                is GroupModel -> avatarCacheService.getGroupAvatar(receiverModel, AvatarOptions.PRESET_DEFAULT_FALLBACK)
                is DistributionListModel -> avatarCacheService.getDistributionListAvatarLow(receiverModel)
                else -> null
            }?.asImageBitmap()
        }
    }

    Avatar(
        modifier = modifier,
        avatar = avatarBitmap,
        contentDescription = contentDescription,
        fallbackIcon = fallbackIcon,
        showWorkBadge = showWorkBadge,
    )
}

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    avatar: ImageBitmap?,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int = R.drawable.ic_contact,
    showWorkBadge: Boolean,
) {
    Box(
        modifier = modifier.size(GridUnit.x5),
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(fallbackIcon),
            contentDescription = contentDescription,
            tint = LocalContentColor.current.copy(
                alpha = 0.6f,
            ),
        )
        AnimatedVisibility(
            visible = avatar != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (avatar != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = avatar,
                    contentDescription = contentDescription,
                )
            }
        }
        if (showWorkBadge) {
            Image(
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .align(Alignment.BottomStart),
                painter = painterResource(R.drawable.ic_badge_work),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun PreviewFallback(
    receiverModel: ReceiverModel,
    showWorkBadge: Boolean,
) {
    Avatar(
        avatar = null,
        contentDescription = null,
        fallbackIcon = when (receiverModel) {
            is GroupModel -> R.drawable.ic_group
            is DistributionListModel -> R.drawable.ic_distribution_list
            else -> R.drawable.ic_contact
        },
        showWorkBadge = showWorkBadge,
    )
}

@Preview
@Composable
private fun AvatarAsync_Preview() {
    ThreemaThemePreview {
        Avatar(
            avatar = null,
            contentDescription = null,
            showWorkBadge = true,
        )
    }
}
