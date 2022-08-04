/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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
import ch.threema.app.services.GroupService
import ch.threema.app.utils.AvatarConverterUtil
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ColorUtil
import ch.threema.storage.models.GroupModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the avatars from the database or create the default avatars. The results of the loaded bitmaps will be cached by glide (if possible).
 */
class GroupAvatarFetcher(context: Context, private val groupService: GroupService?, private val config: AvatarCacheServiceImpl.GroupAvatarConfig) : AvatarFetcher(context) {

    private val groupDefaultAvatar: VectorDrawableCompat? by lazy { VectorDrawableCompat.create(context.resources, R.drawable.ic_group, null) }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        if (config.options.defaultOnly) {
            callback.onDataReady(buildDefaultAvatar(config.model, config.options.highRes))
        } else {
            callback.onDataReady(loadGroupAvatar(config.model, config.options.highRes, config.options.returnDefaultAvatarIfNone))

        }
    }

    private fun loadGroupAvatar(groupModel: GroupModel?, highRes: Boolean, returnDefaultAvatarIfNone: Boolean): Bitmap? {
        var groupImage: Bitmap? = fileService?.getGroupAvatar(groupModel)
        if (groupImage != null && !highRes) {
            //resize image!
            val converted = AvatarConverterUtil.convert(context.resources, groupImage)
            if (groupImage != converted) {
                BitmapUtil.recycle(groupImage)
            }
            return converted
        }
        if (groupImage == null && returnDefaultAvatarIfNone) {
            groupImage = buildDefaultAvatar(groupModel, highRes)
        }
        return groupImage
    }

    private fun buildDefaultAvatar(groupModel: GroupModel?, highRes: Boolean): Bitmap {
        val color = groupService?.getAvatarColor(groupModel) ?: ColorUtil.getInstance().getCurrentThemeGray(context)
        return if (highRes) {
            buildDefaultAvatarHighRes(groupDefaultAvatar, color)
        } else {
            buildDefaultAvatarLowRes(groupDefaultAvatar, color)
        }
    }

}
