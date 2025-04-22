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
import ch.threema.app.services.DistributionListService
import ch.threema.app.utils.ColorUtil
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the avatars from the database or create the default avatars. The computation/loading of these kind of bitmaps is faster than for contacts
 * or groups as only low resolution default avatars are needed.
 */
class DistributionListAvatarFetcher(
    context: Context,
    private val distributionListService: DistributionListService?,
    private val distributionListConfig: AvatarCacheServiceImpl.DistributionListAvatarConfig,
) : AvatarFetcher(context) {
    private val distributionListDefaultAvatar: VectorDrawableCompat? by lazy {
        VectorDrawableCompat.create(
            context.resources,
            R.drawable.ic_distribution_list,
            null,
        )
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val color = distributionListService?.getAvatarColor(distributionListConfig.model)
            ?: ColorUtil.getInstance().getCurrentThemeGray(context)
        callback.onDataReady(buildDefaultAvatarLowRes(distributionListDefaultAvatar, color))
    }
}
