/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.utils.AvatarConverterUtil
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ColorUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the avatars from the database or create the default avatars. The results of the loaded bitmaps will be cached by glide (if possible).
 */
class GroupAvatarFetcher(
    context: Context,
    private val groupModelRepository: GroupModelRepository?,
    private val config: AvatarCacheServiceImpl.GroupAvatarConfig,
) : AvatarFetcher(context) {
    private val groupDefaultAvatar: VectorDrawableCompat? by lazy {
        VectorDrawableCompat.create(
            context.resources,
            R.drawable.ic_group,
            null,
        )
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val groupModel = config.model?.let {
            groupModelRepository?.getByCreatorIdentityAndId(it.creatorIdentity, it.apiGroupId)
        }
        val defaultAvatar: Boolean
        val defaultAvatarIfNone: Boolean
        when (config.options.defaultAvatarPolicy) {
            AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK -> {
                defaultAvatar = false
                defaultAvatarIfNone = true
            }

            AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR -> {
                defaultAvatar = false
                defaultAvatarIfNone = false
            }

            AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR -> {
                defaultAvatar = true
                defaultAvatarIfNone = true
            }
        }
        val backgroundColor = getBackgroundColor(config.options)

        if (defaultAvatar || groupModel == null) {
            callback.onDataReady(
                buildDefaultAvatar(
                    groupModel,
                    config.options.highRes,
                    backgroundColor,
                ),
            )
        } else {
            callback.onDataReady(
                loadGroupAvatar(
                    groupModel,
                    config.options.highRes,
                    defaultAvatarIfNone,
                    backgroundColor,
                ),
            )
        }
    }

    private fun loadGroupAvatar(
        groupModel: GroupModel,
        highRes: Boolean,
        returnDefaultAvatarIfNone: Boolean,
        backgroundColor: Int,
    ): Bitmap? {
        var groupImage: Bitmap? = fileService?.getGroupAvatar(groupModel)
        if (groupImage != null && !highRes) {
            // resize image!
            val converted = AvatarConverterUtil.convert(context.resources, groupImage)
            if (groupImage != converted) {
                BitmapUtil.recycle(groupImage)
            }
            return converted
        }
        if (groupImage == null && returnDefaultAvatarIfNone) {
            groupImage = buildDefaultAvatar(groupModel, highRes, backgroundColor)
        }
        return groupImage
    }

    private fun buildDefaultAvatar(
        groupModel: GroupModel?,
        highRes: Boolean,
        backgroundColor: Int,
    ): Bitmap {
        val color = groupModel?.data?.value?.getThemedColor(context)
            ?: ColorUtil.getInstance().getCurrentThemeGray(context)
        return if (highRes) {
            buildDefaultAvatarHighRes(groupDefaultAvatar, color, backgroundColor)
        } else {
            buildDefaultAvatarLowRes(groupDefaultAvatar, color)
        }
    }
}
