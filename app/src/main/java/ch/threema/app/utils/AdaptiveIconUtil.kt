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

package ch.threema.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale

object AdaptiveIconUtil {

    /**
     * Creates an adaptive icon from the given [bitmap].
     * The icon is cropped to a circle and includes appropriate amounts of padding, such that on most systems it appears to fill the available
     * space of the used shape, by accepting small amounts of cropping.
     * Returns `null` if adaptive icons are not supported by the system.
     */
    @JvmStatic
    fun create(context: Context, bitmap: Bitmap): IconCompat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }

        val density = context.resources.displayMetrics.density
        val outerSize = (OUTER_SIZE * density).toInt()
        val innerSize = (INNER_SIZE * density).toInt()
        val offset = (outerSize - innerSize) / 2f

        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val scaledBitmap = bitmap.scale(innerSize, innerSize, false)
        val paddedBitmap = createBitmap(outerSize, outerSize)
        try {
            paddedBitmap.applyCanvas {
                drawColor(Color.TRANSPARENT)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                paint.isAntiAlias = true
                drawCircle(outerSize / 2f, outerSize / 2f, innerSize / 2f, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                drawBitmap(scaledBitmap, offset, offset, paint)
            }
            return IconCompat.createWithAdaptiveBitmap(paddedBitmap)
        } finally {
            scaledBitmap.recycle()
        }
    }

    /**
     * The width and height in dp of the resulting bitmap, as per the adaptive icon specifications.
     */
    private const val OUTER_SIZE = 108

    /**
     * The width and height in dp of the avatar itself centered within the resulting bitmap. Intentionally set to be slightly larger
     * than the 72 dp of the adaptive icon specifications, which can lead to slight cropping of the image when displayed but reduces
     * the likelihood that the circular cropping will be visible, leading to a nicer appearance on most systems.
     */
    private const val INNER_SIZE = 78
}
