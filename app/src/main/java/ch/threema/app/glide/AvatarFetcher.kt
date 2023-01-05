/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.FileService
import ch.threema.app.utils.AvatarConverterUtil
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class provides the basic functionality to build default avatars. It must be overridden by specific avatar fetchers.
 */
abstract class AvatarFetcher(protected val context: Context) : DataFetcher<Bitmap> {

    protected val fileService: FileService? by lazy { ThreemaApplication.getServiceManager()?.fileService }
    private val avatarSizeSmall: Int by lazy { context.resources.getDimensionPixelSize(R.dimen.avatar_size_small) }
    private val avatarSizeHiRes: Int by lazy { context.resources.getDimensionPixelSize(R.dimen.avatar_size_hires)}

    override fun cleanup() {
        // Nothing to cleanup
    }

    override fun cancel() {
        // Nothing to do here
    }

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    protected fun getBackgroundColor(options: AvatarOptions): Int {
        return if (options.darkerBackground)
            ContextCompat.getColor(context, R.color.material_grey_300)
        else
            Color.WHITE
    }

    /**
     * Create a bitmap of the given drawable with the given color in high resolution. Used for large views like
     * in GroupDetailActivity.
     */
    protected fun buildDefaultAvatarHighRes(drawable: VectorDrawableCompat?, color: Int, backgroundColor: Int): Bitmap {
        return AvatarConverterUtil.buildDefaultAvatarHighRes(drawable, avatarSizeHiRes, color, backgroundColor)
    }

    /**
     * Create a bitmap of the given drawable with the given color in low resolution. Used for smaller views
     * like in ContactListAdapter.
     */
    protected fun buildDefaultAvatarLowRes(drawable: VectorDrawableCompat?, color: Int): Bitmap {
        return AvatarConverterUtil.getAvatarBitmap(drawable, color, avatarSizeSmall)
    }

}
