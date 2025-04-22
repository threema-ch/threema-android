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
import android.widget.TextView
import ch.threema.app.R
import com.google.android.material.card.MaterialCardView

class MoreReactionsButton : MaterialCardView {
    private var labelText: String? = null
    var onMoreReactionsButtonClickListener: OnMoreReactionsButtonClickListener? = null

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onMoreReactionsButtonClickListener?.onMoreReactionsButtonClick()
                    performClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    onMoreReactionsButtonClickListener?.onMoreReactionsButtonLongClick()
                    performLongClick()
                }
            },
        )

    private lateinit var labelView: TextView

    constructor(context: Context) : super(
        context,
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
        context,
        attrs,
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        init()
    }

    private fun init() {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isClickable = true
        isFocusable = true
        isDuplicateParentStateEnabled = false

        LayoutInflater.from(context).inflate(R.layout.view_more_reactions_button, this as ViewGroup)

        labelView = findViewById(R.id.reaction_label)
    }

    private fun renderContents() {
        labelView.text = labelText
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

    fun setButtonData(text: String) {
        labelText = text
        renderContents()
    }

    interface OnMoreReactionsButtonClickListener {
        fun onMoreReactionsButtonClick()
        fun onMoreReactionsButtonLongClick()
    }
}
