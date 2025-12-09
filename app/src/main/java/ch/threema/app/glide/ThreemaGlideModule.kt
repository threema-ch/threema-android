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
import ch.threema.app.services.AvatarCacheServiceImpl.DistributionListAvatarConfig
import ch.threema.app.services.AvatarCacheServiceImpl.GroupAvatarConfig
import ch.threema.app.services.AvatarCacheServiceImpl.IdentityAvatarConfig
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class ThreemaGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_ARGB_8888))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        with(registry) {
            prepend(IdentityAvatarConfig::class.java, Bitmap::class.java, IdentityAvatarModelLoaderFactory(context))
            prepend(GroupAvatarConfig::class.java, Bitmap::class.java, GroupAvatarModelLoaderFactory(context))
            prepend(DistributionListAvatarConfig::class.java, Bitmap::class.java, DistributionListAvatarModelLoaderFactory(context))
            prepend(AbstractMessageModel::class.java, Bitmap::class.java, ThumbnailLoaderFactory())
        }
    }
}
