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
import ch.threema.app.di.injectNonBinding
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.services.DistributionListService
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class DistributionListAvatarLoader(
    private val context: Context,
) :
    ModelLoader<AvatarCacheServiceImpl.DistributionListAvatarConfig, Bitmap>, KoinComponent {
    private val distributionListService: DistributionListService? by injectNonBinding()

    override fun buildLoadData(
        config: AvatarCacheServiceImpl.DistributionListAvatarConfig,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(
            ObjectKey(config),
            DistributionListAvatarFetcher(
                context = context,
                distributionListService = distributionListService ?: return null,
                distributionListConfig = config,
            ),
        )
    }

    override fun handles(model: AvatarCacheServiceImpl.DistributionListAvatarConfig) = true
}
