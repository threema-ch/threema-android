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

package ch.threema.app.qrcodes

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import ch.threema.base.utils.getThreemaLogger
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private val logger = getThreemaLogger("QrCodeGenerator")

class QrCodeGenerator {
    fun generate(content: String): Bitmap? =
        generate(content, includeUtf8Hint = false)

    fun generateWithUnicodeSupport(content: String): Bitmap? =
        generate(content, includeUtf8Hint = true)

    private fun generate(content: String, includeUtf8Hint: Boolean): Bitmap? {
        val matrix = render(content, includeUtf8Hint)
            ?: return null
        val bitmap = createBitmap(width = matrix.width, height = matrix.height, config = Bitmap.Config.RGB_565)
        bitmap.setPixels(matrix.toPixelArray(), 0, matrix.width, 0, 0, matrix.width, matrix.height)
        return bitmap
    }

    private fun render(content: String, includeUtf8Hint: Boolean): BitMatrix? {
        val hints = buildMap {
            put(EncodeHintType.MARGIN, QUIET_ZONE_SIZE)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q)
            if (includeUtf8Hint) {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
        }

        return try {
            QRCodeWriter().encode(
                /* contents = */
                content,
                /* format = */
                BarcodeFormat.QR_CODE,
                /* width = */
                0,
                /* height = */
                0,
                /* hints = */
                hints,
            )
        } catch (e: WriterException) {
            logger.error("Failed to render QR code", e)
            null
        }
    }

    private fun BitMatrix.toPixelArray(): IntArray {
        val pixels = IntArray(width * height)
        repeat(height) { y ->
            val offset = y * width
            repeat(width) { x ->
                pixels[offset + x] = if (get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return pixels
    }

    companion object {
        /**
         * See https://www.qrcode.com/en/howto/code.html for details
         */
        private const val QUIET_ZONE_SIZE = 4
    }
}
