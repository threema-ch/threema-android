/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.emojis

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import ch.threema.app.R
import ch.threema.app.utils.RuntimeUtil
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.ExecutionException
import java8.util.concurrent.CompletableFuture
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("EmojiManager")

class EmojiManager private constructor(context: Context) {

    @JvmField
    val spritemapInSampleSize: Int =
        if (context.resources.displayMetrics.density <= 1f) 2 else 1

    private val appContext: Context = context.applicationContext

    /**
     * @param emojiSequence - sequence of UTF-8 characters representing the emoji
     *
     * @return [EmojiDrawable] or `null` if no asset exists for the passed [emojiSequence]
     */
    fun getEmojiDrawableAsync(emojiSequence: String?): EmojiDrawable? =
        EmojiParser.parseAt(emojiSequence, 0)?.let {
            getEmojiDrawableAsync(it.coords)
        }

    /**
     * Loads the [EmojiDrawable] asynchronously for the passed sprite coordinates.
     *
     * @return [EmojiDrawable] or `null` if no asset exists at the given [coordinates]
     */
    @UiThread
    fun getEmojiDrawableAsync(coordinates: SpriteCoordinates): EmojiDrawable? {
        val emojiGroup: EmojiGroup = emojiGroups[coordinates.groupId]
        if (!emojiGroup.hasSpritemapBitmap(coordinates.spritemapId)) {
            emojiGroup.setSpritemapBitmap(
                coordinates.spritemapId,
                EmojiSpritemapBitmap(appContext, emojiGroup, coordinates.spritemapId, spritemapInSampleSize),
            )
        }

        val emojiDrawable = EmojiDrawable(coordinates, spritemapInSampleSize)

        val spriteBitmap: EmojiSpritemapBitmap? = emojiGroup.getSpritemapBitmap(coordinates.spritemapId)
        if (spriteBitmap == null) {
            logger.error("Failed to read emoji drawable for coordinates: {}", coordinates)
            return null
        }

        if (spriteBitmap.isSpritemapLoaded) {
            emojiDrawable.setBitmap(spriteBitmap.spritemapBitmap)
        } else {
            try {
                CompletableFuture
                    .supplyAsync { spriteBitmap.loadSpritemapAsset() }
                    .thenAccept { bitmap: Bitmap? -> RuntimeUtil.runOnUiThread { emojiDrawable.setBitmap(bitmap) } }
                    .get()
            } catch (e: InterruptedException) {
                logger.error("Exception while reading emoji drawable", e)
                return null
            } catch (e: ExecutionException) {
                logger.error("Exception while reading emoji drawable", e)
                return null
            }
        }
        return emojiDrawable
    }

    @WorkerThread
    suspend fun getEmojiDrawableSynchronously(coordinates: SpriteCoordinates): EmojiDrawable? = withContext(Dispatchers.IO) {
        val emojiGroup: EmojiGroup = emojiGroups[coordinates.groupId]
        if (!emojiGroup.hasSpritemapBitmap(coordinates.spritemapId)) {
            emojiGroup.setSpritemapBitmap(
                coordinates.spritemapId,
                EmojiSpritemapBitmap(appContext, emojiGroup, coordinates.spritemapId, spritemapInSampleSize),
            )
        }

        val emojiDrawable = EmojiDrawable(coordinates, spritemapInSampleSize)

        val spriteBitmap: EmojiSpritemapBitmap? = emojiGroup.getSpritemapBitmap(coordinates.spritemapId)
        if (spriteBitmap == null) {
            logger.error("Failed to read emoji drawable for coordinates: {}", coordinates)
            return@withContext null
        }

        if (spriteBitmap.isSpritemapLoaded) {
            emojiDrawable.setBitmap(spriteBitmap.spritemapBitmap)
        } else {
            try {
                spriteBitmap.loadSpritemapAsset().also(emojiDrawable::setBitmap)
            } catch (e: InterruptedException) {
                logger.error("Exception while reading emoji drawable", e)
                return@withContext null
            } catch (e: ExecutionException) {
                logger.error("Exception while reading emoji drawable", e)
                return@withContext null
            }
        }
        return@withContext emojiDrawable
    }

    companion object {

        @Volatile
        private var instance: EmojiManager? = null

        @JvmStatic
        fun getInstance(context: Context): EmojiManager =
            instance ?: synchronized(this) {
                instance ?: EmojiManager(context).also { instance = it }
            }

        const val EMOJI_HEIGHT: Int = 64
        const val EMOJI_WIDTH: Int = 64

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
