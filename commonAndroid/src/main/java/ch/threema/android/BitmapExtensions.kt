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

package ch.threema.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.IntRange
import java.io.ByteArrayOutputStream

fun Bitmap.toJpegByteArray(quality: Int = 80): ByteArray? =
    toByteArray(format = Bitmap.CompressFormat.JPEG, quality = quality)

private fun Bitmap.toByteArray(format: Bitmap.CompressFormat, quality: Int): ByteArray? {
    val stream = ByteArrayOutputStream()
    return if (compress(format, quality, stream)) {
        stream.toByteArray()
    } else {
        null
    }
}

/**
 * Calculates the estimated brightness of an Android Bitmap.
 *
 * @param pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
 * When [pixelSpacing] = 1, the function actually calculates the real average brightness, not an estimate.
 * Do not use values for [pixelSpacing] that are smaller than 1.
 */
@JvmOverloads
fun Bitmap.calculateBrightness(@IntRange(from = 1) pixelSpacing: Int = 2): Int {
    val bitmap = this
    val width = bitmap.getWidth()
    val height = bitmap.getHeight()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var red = 0
    var green = 0
    var blue = 0
    var n = 0
    var i = 0
    while (i < pixels.size) {
        val color = pixels[i]
        red += Color.red(color)
        green += Color.green(color)
        blue += Color.blue(color)
        n++
        i += pixelSpacing
    }
    if (n != 0) {
        return (red + blue + green) / (n * 3)
    }
    return 0
}
