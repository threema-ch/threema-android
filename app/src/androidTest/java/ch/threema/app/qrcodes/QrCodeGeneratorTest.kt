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
import ch.threema.testhelpers.loadResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QrCodeGeneratorTest {

    @Test
    fun generateQrCode() {
        val bitmap = QrCodeGenerator().generateWithUnicodeSupport(content = "äbc-123 \uD83D\uDC08")
        assertNotNull(bitmap)

        try {
            assertEquals(
                loadResource("qr-code.txt"),
                bitmap.toPixelString(),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun Bitmap.toPixelString(): String {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.toList()
            .chunked(width)
            .joinToString(separator = "\n") { pixelRow ->
                pixelRow.joinToString(separator = "") { pixelColor ->
                    when (pixelColor) {
                        BLACK -> "■"
                        WHITE -> " "
                        else -> "?"
                    }
                }
            }
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
