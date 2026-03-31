package ch.threema.app.emojis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.AnyThread
import androidx.annotation.IntRange
import ch.threema.app.utils.RuntimeUtil
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CompletableDeferred

private val logger = getThreemaLogger("EmojiDrawable")

class EmojiDrawable(
    private val coordinates: SpriteCoordinates,
    private val spritemapInSampleSize: Int,
) : Drawable() {
    private var bitmap: Bitmap? = null

    /**
     * Tracks whether the bitmap was already loaded. Will be set to 'completed' and then set to null to free up memory,
     * as once the bitmap is loaded it remains in this state.
     */
    private var loadedDeferred: CompletableDeferred<Unit>? = CompletableDeferred()

    override fun getIntrinsicWidth() =
        spritemapInSampleSize * EMOJI_WIDTH

    override fun getIntrinsicHeight() =
        spritemapInSampleSize * EMOJI_HEIGHT

    override fun draw(canvas: Canvas) {
        val bitmap = bitmap ?: return
        canvas.drawBitmap(
            bitmap,
            Rect(
                ((coordinates.x).toFloat() / spritemapInSampleSize).toInt(),
                (coordinates.y.toFloat() / spritemapInSampleSize).toInt(),
                ((coordinates.x + EMOJI_WIDTH).toFloat() / spritemapInSampleSize).toInt(),
                ((coordinates.y + EMOJI_HEIGHT).toFloat() / spritemapInSampleSize).toInt(),
            ),
            getBounds(),
            paint,
        )
    }

    @AnyThread
    fun setBitmap(bitmap: Bitmap) {
        if (this.bitmap != null) {
            logger.error("Bitmap already set for {}", coordinates)
            return
        }
        this.bitmap = bitmap
        loadedDeferred?.complete(Unit)
        loadedDeferred = null
        RuntimeUtil.runOnUiThread {
            invalidateSelf()
        }
    }

    /**
     * Indicates that the loading of the bitmap for this [EmojiDrawable] has failed, which will cause [awaitLoaded]
     * to throw an [EmojiLoadingException]. Will have no effect if the bitmap was already loaded.
     */
    @AnyThread
    fun setFailed() {
        loadedDeferred?.completeExceptionally(EmojiLoadingException())
    }

    /**
     * Suspends until the bitmap for this [EmojiDrawable] is loaded.
     *
     * @throws EmojiLoadingException if loading failed.
     */
    suspend fun awaitLoaded() {
        loadedDeferred?.await()
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() =
        PixelFormat.TRANSPARENT

    class EmojiLoadingException : Exception()

    companion object {
        private const val EMOJI_HEIGHT = 64
        private const val EMOJI_WIDTH = 64

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    }
}
