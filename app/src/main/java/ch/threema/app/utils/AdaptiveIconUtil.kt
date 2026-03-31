package ch.threema.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale

object AdaptiveIconUtil {

    /**
     * Creates an adaptive icon from the given [bitmap].
     * The icon includes appropriate amounts of padding, such that on most systems it appears to fill the available
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
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
     * the likelihood that the edges of the image will be visible, leading to a nicer appearance on most systems.
     */
    private const val INNER_SIZE = 76
}
