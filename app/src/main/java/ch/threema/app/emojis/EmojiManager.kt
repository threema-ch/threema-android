package ch.threema.app.emojis

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import ch.threema.app.R
import ch.threema.app.utils.DispatcherProvider
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EmojiManager private constructor(
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.io)

    @JvmField
    val spritemapInSampleSize: Int =
        if (appContext.resources.displayMetrics.density <= 1f) 2 else 1

    /**
     * @param emojiSequence - sequence of UTF-8 characters representing the emoji
     *
     * @return [EmojiDrawable] or `null` if no asset exists for the passed [emojiSequence]
     */
    @AnyThread
    fun getEmojiDrawableAsync(emojiSequence: String?): EmojiDrawable? =
        emojiSequence
            ?.let { EmojiParser.parseAt(emojiSequence, 0) }
            ?.let { getEmojiDrawableAsync(it.coords) }

    /**
     * Returns the [EmojiDrawable] for the passed sprite coordinates and asynchronously starts loading the
     * required spritemap bitmap.
     */
    @AnyThread
    fun getEmojiDrawableAsync(coordinates: SpriteCoordinates): EmojiDrawable {
        val emojiDrawable = EmojiDrawable(coordinates, spritemapInSampleSize)
        val spritemapBitmap = getSpritemapBitmap(coordinates)
            ?: return emojiDrawable
        val isLoaded = applyBitmapIfLoaded(spritemapBitmap, emojiDrawable)
        if (!isLoaded) {
            coroutineScope.launch {
                val bitmap = spritemapBitmap.getOrLoadSpritemapAsset()
                if (bitmap != null) {
                    emojiDrawable.setBitmap(bitmap)
                } else {
                    emojiDrawable.setFailed()
                }
            }
        }
        return emojiDrawable
    }

    private fun getSpritemapBitmap(coordinates: SpriteCoordinates): EmojiSpritemapBitmap? {
        val emojiGroup = emojiGroups[coordinates.groupId]
        emojiGroup.getSpritemapBitmap(coordinates.spritemapId)
            ?.let { spriteBitmap ->
                return spriteBitmap
            }
        synchronized(emojiGroup) {
            // We check whether a spritemap bitmap exists again inside the synchronized block.
            // This ensures that we set the bitmap at most once even if multiple callers try to acquire the lock.
            emojiGroup.getSpritemapBitmap(coordinates.spritemapId)
                ?.let { spriteBitmap ->
                    return spriteBitmap
                }
            val assetPath = emojiGroup.getAssetPath(coordinates.spritemapId)
                ?: return null
            val spriteBitmap = EmojiSpritemapBitmap(
                appContext,
                dispatcherProvider,
                assetPath,
                spritemapInSampleSize,
            )
            emojiGroup.setSpritemapBitmap(coordinates.spritemapId, spriteBitmap)
            return spriteBitmap
        }
    }

    private fun applyBitmapIfLoaded(spritemapBitmap: EmojiSpritemapBitmap, emojiDrawable: EmojiDrawable): Boolean =
        spritemapBitmap.spritemapBitmap
            ?.let { bitmap ->
                emojiDrawable.setBitmap(bitmap)
                true
            }
            ?: false

    companion object {

        @Volatile
        private var instance: EmojiManager? = null

        @JvmStatic
        fun getInstance(context: Context): EmojiManager =
            instance ?: synchronized(this) {
                instance ?: EmojiManager(context.applicationContext).also { instance = it }
            }

        @JvmField
        val emojiGroups: Array<EmojiGroup> = arrayOf(
            EmojiGroup(null, null, R.drawable.emoji_category_recent, R.string.emoji_recent),
            EmojiGroup("emojis/smileys-", ".png", R.drawable.emoji_category_smileys, R.string.emoji_smileys),
            EmojiGroup("emojis/people-", ".png", R.drawable.emoji_category_people, R.string.emoji_people),
            EmojiGroup("emojis/nature-", ".png", R.drawable.emoji_category_nature, R.string.emoji_nature),
            EmojiGroup("emojis/food-", ".png", R.drawable.emoji_category_food, R.string.emoji_food),
            EmojiGroup("emojis/activity-", ".png", R.drawable.emoji_category_activities, R.string.emoji_activity),
            EmojiGroup("emojis/travel-", ".png", R.drawable.emoji_category_travel, R.string.emoji_travel),
            EmojiGroup("emojis/objects-", ".png", R.drawable.emoji_category_objects, R.string.emoji_objects),
            EmojiGroup("emojis/symbols-", ".png", R.drawable.emoji_category_symbols, R.string.emoji_symbols),
            EmojiGroup("emojis/flags-", ".png", R.drawable.emoji_category_flags, R.string.emoji_flags),
        )

        @JvmStatic
        val numberOfEmojiGroups: Int
            get() = emojiGroups.size

        @JvmStatic
        @StringRes
        fun getEmojiGroupName(id: Int): Int = emojiGroups[id].groupName
    }
}
