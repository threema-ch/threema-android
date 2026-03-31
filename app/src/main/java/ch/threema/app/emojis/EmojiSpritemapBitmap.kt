package ch.threema.app.emojis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.AnyThread
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import java.lang.ref.SoftReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("EmojiSpritemapBitmap")

class EmojiSpritemapBitmap(
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val assetPath: String,
    private val spritemapInSampleSize: Int,
) {
    private val mutex = Mutex()

    private var bitmapReference: SoftReference<Bitmap>? = null

    @get:AnyThread
    var spritemapBitmap: Bitmap?
        get() = bitmapReference?.get()
        private set(value) {
            bitmapReference = value?.let { SoftReference<Bitmap>(value) }
        }

    /**
     * Returns the spritemap bitmap if it is already loaded, or starts loading it and then returns it.
     * It is safe to call this method multiple times, as we ensure internally that the bitmap is at most loaded once
     * and all calls will suspend until that bitmap is loaded.
     * Returns null if loading fails, in which case the method may be called again to retry loading.
     */
    suspend fun getOrLoadSpritemapAsset(): Bitmap? {
        spritemapBitmap?.let {
            return it
        }
        mutex.withLock {
            // we intentionally check spritemapBitmap again here, to avoid loading the bitmap again
            // in case the method gets called multiple times
            spritemapBitmap?.let { bitmap ->
                return bitmap
            }
            try {
                logger.debug("*** Loading Emoji spritemap for group {}", assetPath)
                val spritemapBitmap: Bitmap? = withContext(dispatcherProvider.io) {
                    appContext.assets.open(assetPath).use { inputStream ->
                        val options = BitmapFactory.Options()
                            .apply {
                                inSampleSize = spritemapInSampleSize
                            }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                }

                if (spritemapBitmap != null) {
                    this.spritemapBitmap = spritemapBitmap
                    return spritemapBitmap
                } else {
                    logger.warn("Failed to load spritemap for group {}", assetPath)
                }
            } catch (e: Exception) {
                logger.error("Failed to load emoji spritemap", e)
            }
        }
        return null
    }

    override fun toString() = assetPath
}
