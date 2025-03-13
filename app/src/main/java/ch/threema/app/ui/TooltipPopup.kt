/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.BadTokenException
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.utils.ConfigUtils
import com.google.android.material.card.MaterialCardView


class TooltipPopup
@JvmOverloads
constructor(
    private val context: Context,
    @StringRes
    preferenceKey: Int = 0,
    lifecycleOwner: LifecycleOwner? = null,
    @DrawableRes
    private val icon: Int = 0,
    private val showCloseButton: Boolean = true,
) : PopupWindow(context), DefaultLifecycleObserver {
    private var popupLayout: View
    private var titleView: EmojiTextView
    private var textView: EmojiTextView
    private var preferenceString: String? = null
    private var timeoutHandler: Handler? = null
    private val dismissRunnable = Runnable {
        listener.onTimedOut(this)
        dismiss(false)
    }

    var listener: TooltipPopupListener = TooltipPopupListener()

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
        preferenceString = if (preferenceKey != 0) {
            context.getString(preferenceKey)
        } else {
            null
        }

        val layoutInflater = context.getSystemService<LayoutInflater>()!!
        popupLayout = layoutInflater.inflate(R.layout.popup_tooltip, null, false)!!
        titleView = popupLayout.findViewById(R.id.title)
        textView = popupLayout.findViewById(R.id.label)
        contentView = popupLayout
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        animationStyle = R.style.TooltipAnimation
        isFocusable = false
        isTouchable = true
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable())

        popupLayout.setOnClickListener {
            listener.onClicked(this)
        }

        popupLayout.findViewById<View>(R.id.close_button)
            ?.let { closeButton ->
                closeButton.isVisible = showCloseButton
                closeButton.setOnClickListener {
                    listener.onCloseButtonClicked(this)
                }
            }
    }

    fun dismissForever() {
        if (preferenceString != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                ?.edit {
                    putBoolean(preferenceString, true)
                }
        }

        dismiss(false)
    }

    fun dismiss(immediate: Boolean) {
        if (immediate) {
            animationStyle = 0
        }

        timeoutHandler?.removeCallbacks(dismissRunnable)
        timeoutHandler = null

        this.dismiss()
        listener.onDismissed(this)
    }

    /**
     * Show a tooltip at the specified location pointing to a specified anchor view
     *
     * @param activity       Activity context
     * @param anchor         Anchor / parent view to of this tooltip
     * @param title          Optional title text to show in tooltip
     * @param text           Text to show in tooltip
     * @param alignment         Where to align the tooltip and where the arrow should be shown
     * @param originLocation The location on screen where the tip of the arrow should point to
     * @param timeoutMs      How long the tooltip should be shown until it fades out
     */
    fun show(
        activity: Activity,
        anchor: View,
        title: String? = null,
        text: String?,
        alignment: Alignment,
        originLocation: IntArray,
        timeoutMs: Int = 0,
    ) {
        if (isForeverDismissed()) {
            return
        }

        if (title != null) {
            titleView.text = title
            titleView.isVisible = true
        } else {
            titleView.isVisible = false
        }
        textView.text = text

        val resources = context.resources

        val screenHeight: Int
        val screenWidth: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val rect = windowMetrics.bounds
            screenWidth = rect.right
            screenHeight = rect.bottom
        } else {
            screenWidth = resources.displayMetrics.widthPixels
            screenHeight = resources.displayMetrics.heightPixels + ConfigUtils.getNavigationBarHeight(activity)
        }
        val maxWidth = resources.getDimensionPixelSize(R.dimen.tooltip_max_width)
        val arrowInset = resources.getDimensionPixelSize(R.dimen.tooltip_popup_arrow_inset)
        val marginOnOtherEdge = resources.getDimensionPixelSize(R.dimen.tooltip_margin_on_other_edge)
        val arrowOffset = (resources.getDimensionPixelSize(R.dimen.identity_popup_arrow_width) / 2) + arrowInset
        var popupX: Int
        val popupY: Int
        val popupWidth: Int
        val anchorGravity: Int
        val contentGravity: Int

        when (alignment) {
            Alignment.ABOVE_ANCHOR_ARROW_LEFT -> {
                popupLayout.findViewById<View>(R.id.arrow_bottom_left).isVisible = true
                popupX = (originLocation[0] - arrowOffset).coerceAtLeast(0)
                popupY = screenHeight - originLocation[1]
                popupWidth = (screenWidth - popupX - marginOnOtherEdge).coerceAtMost(maxWidth)
                anchorGravity = Gravity.LEFT or Gravity.BOTTOM
                contentGravity = Gravity.LEFT
            }

            Alignment.ABOVE_ANCHOR_ARROW_RIGHT -> {
                popupLayout.findViewById<View>(R.id.arrow_bottom_right).isVisible = true
                popupX = (originLocation[0] + arrowOffset).coerceAtMost(screenWidth)
                popupY = screenHeight - originLocation[1]
                popupWidth = (popupX - marginOnOtherEdge).coerceAtMost(maxWidth)
                popupX -= popupWidth
                anchorGravity = Gravity.LEFT or Gravity.BOTTOM
                contentGravity = Gravity.RIGHT
            }

            Alignment.BELOW_ANCHOR_ARROW_LEFT -> {
                popupLayout.findViewById<View>(R.id.arrow_top_left).isVisible = true
                popupX = (originLocation[0] - arrowOffset).coerceAtLeast(0)
                popupY = originLocation[1]
                popupWidth = (screenWidth - popupX - marginOnOtherEdge).coerceAtMost(maxWidth)
                anchorGravity = Gravity.LEFT or Gravity.TOP
                contentGravity = Gravity.LEFT
            }

            Alignment.BELOW_ANCHOR_ARROW_RIGHT -> {
                popupLayout.findViewById<View>(R.id.arrow_top_right).isVisible = true
                popupX = (originLocation[0] + arrowOffset).coerceAtMost(screenWidth)
                popupY = originLocation[1]
                popupWidth = (popupX - marginOnOtherEdge).coerceAtMost(maxWidth)
                popupX -= popupWidth
                anchorGravity = Gravity.LEFT or Gravity.TOP
                contentGravity = Gravity.RIGHT
            }
        }

        this.width = popupWidth
        this.height = ViewGroup.LayoutParams.WRAP_CONTENT
        val contentLayout = popupLayout.findViewById<MaterialCardView>(R.id.content)
        val params = contentLayout.layoutParams as FrameLayout.LayoutParams
        params.gravity = contentGravity
        contentLayout.layoutParams = params

        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        try {
            showAtLocation(anchor, anchorGravity, popupX, popupY)
        } catch (e: BadTokenException) {
            return
        }

        val iconView = popupLayout.findViewById<ImageView>(R.id.icon)
        if (icon != 0) {
            iconView.setImageResource(icon)
            iconView.isVisible = true
        } else {
            iconView.isVisible = false
        }

        listener.onShown(this)

        if (timeoutMs > 0) {
            if (timeoutHandler == null) {
                timeoutHandler = Handler(Looper.getMainLooper())
            }
            timeoutHandler?.removeCallbacks(dismissRunnable)
            timeoutHandler?.postDelayed(dismissRunnable, timeoutMs.toLong())
        }
    }

    private fun isForeverDismissed(): Boolean {
        if (preferenceString == null) {
            return false
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
            ?.getBoolean(preferenceString, false)
            ?: false
    }

    /**
     * Notifies that `ON_PAUSE` event occurred.
     *
     * This method will be called before the [LifecycleOwner]'s `onPause` method
     * is called.
     *
     * @param owner the component, whose state was changed
     */
    override fun onPause(owner: LifecycleOwner) {
        dismiss(true)
    }

    open class TooltipPopupListener {
        open fun onShown(tooltipPopup: TooltipPopup) {}
        open fun onClicked(tooltipPopup: TooltipPopup) {
            tooltipPopup.dismissForever()
        }

        open fun onCloseButtonClicked(tooltipPopup: TooltipPopup) {
            tooltipPopup.dismissForever()
        }

        open fun onTimedOut(tooltipPopup: TooltipPopup) {}
        open fun onDismissed(tooltipPopup: TooltipPopup) {}
    }

    enum class Alignment {
        ABOVE_ANCHOR_ARROW_LEFT,
        ABOVE_ANCHOR_ARROW_RIGHT,
        BELOW_ANCHOR_ARROW_LEFT,
        BELOW_ANCHOR_ARROW_RIGHT,
    }
}
