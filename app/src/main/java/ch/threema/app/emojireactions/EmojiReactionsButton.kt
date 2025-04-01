/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.emojireactions

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Space
import android.widget.TextView
import androidx.core.view.isVisible
import ch.threema.app.R
import ch.threema.app.emojis.EmojiTextView
import com.google.android.material.card.MaterialCardView

class EmojiReactionsButton : MaterialCardView {
    private var labelText: String? = null
    private var emojiSequence: String? = null

    var onEmojiReactionButtonClickListener: OnEmojiReactionButtonClickListener? = null

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onEmojiReactionButtonClickListener?.onClick(emojiSequence)
                performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onEmojiReactionButtonClickListener?.onLongClick(emojiSequence)
                performLongClick()
            }
        })

    private lateinit var labelView: TextView
    private lateinit var emojiView: EmojiTextView
    private lateinit var spacerView: Space

    constructor(context: Context) : super(
        context
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
        context,
        attrs
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        layoutParams = LayoutParams(WRAP_CONTENT, MATCH_PARENT)
        isClickable = true
        isFocusable = true
        isCheckable = true
        isDuplicateParentStateEnabled = false

        LayoutInflater.from(context).inflate(R.layout.view_emoji_reaction, this as ViewGroup)

        labelView = findViewById(R.id.reaction_label)
        emojiView = findViewById(R.id.reaction_emoji)
        spacerView = findViewById(R.id.reaction_spacer)
    }

    private fun renderContents() {
        emojiView.isVisible = emojiSequence?.isNotEmpty() == true
        if (emojiView.isVisible) {
            emojiSequence = emojiView.setSingleEmojiSequence(emojiSequence).toString()
        }

        labelView.isVisible = labelText?.isNotEmpty() == true
        if (labelView.isVisible) {
            labelView.text = labelText
        }

        spacerView.isVisible = emojiView.isVisible && labelView.isVisible
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            isPressed = false
        } else if (event.action == MotionEvent.ACTION_DOWN) {
            isPressed = true
        }

        return true
    }

    fun setButtonData(emojiSequence: String, count: Int) {
        this.emojiSequence = emojiSequence
        labelText = if (count > 1) count.toString() else ""
        renderContents()
    }

    interface OnEmojiReactionButtonClickListener {
        fun onClick(emojiSequence: String?)
        fun onLongClick(emojiSequence: String?)
    }
}
