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

package ch.threema.app.multidevice.wizard.steps

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.card.MaterialCardView

private val logger = LoggingUtil.getThreemaLogger("LinkNewDeviceEmojiSelectionView")

/**
 * A button-like view that displays a sequence of three rendezvous emojis
 */
class LinkNewDeviceEmojiSelectionView : MaterialCardView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    private val emojiViews = arrayOfNulls<ImageView>(3)
    private val rendezvousEmojis = RendezvousEmojis()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_emoji_selection, this, true)

        setCardBackgroundColor(
            ColorStateList.valueOf(
                ConfigUtils.getColorFromAttribute(context, R.attr.colorPrimaryContainer),
            ),
        )
        strokeWidth = 0
        cardElevation = 0F

        emojiViews[0] = findViewById(R.id.emoji1)
        emojiViews[1] = findViewById(R.id.emoji2)
        emojiViews[2] = findViewById(R.id.emoji3)
    }

    /**
     * Display the provided emojis in this view.
     */
    fun setEmojis(emojiIndexes: IntArray) {
        if (emojiIndexes.size == emojiViews.size) {
            repeat(emojiViews.size) { i ->
                fillEmojiViewContents(emojiViews[i], emojiIndexes[i])
            }
        } else {
            logger.debug("Invalid number of emoji indexes")
        }
    }

    /**
     * Display a set of random emojis in this view. The emojis are not unique so the sequence may contain duplicates
     */
    fun setRandomEmojis() {
        repeat(emojiViews.size) { i ->
            val emojiIndex = (0 until rendezvousEmojis.emojiList.size).random()
            fillEmojiViewContents(emojiViews[i], emojiIndex)
        }
    }

    private fun fillEmojiViewContents(emojiView: ImageView?, emojiIndex: Int) {
        emojiView?.let {
            it.setImageResource(getEmojiResourceID(emojiIndex))
            it.contentDescription = resources.getString(getEmojiStringID(emojiIndex))
        }
    }

    @SuppressLint("DiscouragedApi")
    fun getEmojiResourceID(emojiIndex: Int): Int {
        return resources.getIdentifier(
            "ic_emoji_" + rendezvousEmojis.emojiList[emojiIndex].lowercase(),
            "drawable",
            context.packageName,
        )
    }

    @SuppressLint("DiscouragedApi")
    fun getEmojiStringID(emojiIndex: Int): Int {
        return resources.getIdentifier(
            "rendezvous_emoji_" + rendezvousEmojis.emojiList[emojiIndex],
            "string",
            context.packageName,
        )
    }
}
