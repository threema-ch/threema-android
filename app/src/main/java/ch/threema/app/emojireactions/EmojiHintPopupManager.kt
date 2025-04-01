/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.edit
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.ui.ConversationListView
import ch.threema.app.ui.TooltipPopup
import ch.threema.app.ui.getBottomCenterLocation
import ch.threema.app.ui.getLocation
import ch.threema.app.ui.getTopCenterLocation
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import kotlin.properties.Delegates

class EmojiHintPopupManager(
    private val appContext: Context,
    private val getActivity: () -> Activity?,
    private val getConversationListView: () -> ConversationListView?,
) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)

    private var shownCounter: Int
        get() = sharedPreferences.getInt(
            appContext.getString(R.string.preferences__tooltip_emoji_reactions_shown_counter),
            0,
        )
        set(value) {
            sharedPreferences.edit {
                putInt(
                    appContext.getString(R.string.preferences__tooltip_emoji_reactions_shown_counter),
                    value,
                )
            }
        }
    private var shouldShowPopup = shownCounter < MAX_TIMES_SHOWN

    private val handler = Handler(Looper.getMainLooper())
    private val showPopupRunnable = Runnable(::showPopup)
    private var tooltipPopup: TooltipPopup? = null

    var isScrolling: Boolean by Delegates.observable(false) { _, _, _ ->
        showOrDismissIfNecessary()
    }

    fun showOrDismissIfNecessary() {
        if (isScrolling || !shouldShowPopup) {
            dismiss()
        } else {
            handler.removeCallbacks(showPopupRunnable)
            handler.postDelayed(showPopupRunnable, POPUP_DELAY)
        }
    }

    private fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        tooltipPopup?.dismiss(true)
        tooltipPopup = null
    }

    private fun showPopup() {
        val messageHolder = findSuitableMessageHolder()
            ?: return
        val emojiButton = messageHolder.emojiReactionGroup?.firstReactionButton
            ?: return

        val activity = getActivity() ?: return

        val screenHeight: Int
        val screenWidth: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowBounds = activity.windowManager.currentWindowMetrics.bounds
            screenWidth = windowBounds.right
            screenHeight = windowBounds.bottom
        } else {
            screenWidth = activity.resources.displayMetrics.widthPixels
            screenHeight = activity.resources.displayMetrics.heightPixels
        }

        val alignment: TooltipPopup.Alignment
        var location = emojiButton.getBottomCenterLocation()
        val verticalRatio = location[1] / screenHeight.toFloat()
        if (verticalRatio !in VALID_VERTICAL_SCREEN_RANGE) {
            // don't display the popup if it's too close to (or beyond) the top or bottom
            // edge of the screen, as there might not be enough space to show it, or the
            // anchor view itself might not even be visible
            return
        }

        if (location[1] > screenHeight / 2) {
            location = emojiButton.getTopCenterLocation()
            alignment = if (location[0] > screenWidth / 2) {
                TooltipPopup.Alignment.ABOVE_ANCHOR_ARROW_RIGHT
            } else {
                TooltipPopup.Alignment.ABOVE_ANCHOR_ARROW_LEFT
            }
        } else {
            alignment = if (location[0] > screenWidth / 2) {
                TooltipPopup.Alignment.BELOW_ANCHOR_ARROW_RIGHT
            } else {
                TooltipPopup.Alignment.BELOW_ANCHOR_ARROW_LEFT
            }
        }

        val tooltipPopup = TooltipPopup(
            context = activity,
            preferenceKey = R.string.preferences__tooltip_emoji_reactions_shown,
            lifecycleOwner = activity as? LifecycleOwner,
        )
        tooltipPopup.listener = object : TooltipPopup.TooltipPopupListener() {
            override fun onClicked(tooltipPopup: TooltipPopup) {
                tooltipPopup.dismiss()
                shownCounter++
            }

            override fun onTimedOut(tooltipPopup: TooltipPopup) {
                shownCounter++
            }

            override fun onShown(tooltipPopup: TooltipPopup) {
                pollAnchorLocation(emojiButton, emojiButton.getLocation())
            }
        }

        tooltipPopup.show(
            activity = activity,
            anchor = emojiButton,
            title = activity.getString(R.string.emoji_reactions_popup_hint_title),
            text = activity.getString(R.string.emoji_reactions_popup_hint_text),
            alignment = alignment,
            originLocation = location,
            timeoutMs = POPUP_TIMEOUT,
        )

        this.tooltipPopup = tooltipPopup
        shouldShowPopup = false
    }

    private fun findSuitableMessageHolder(): ComposeMessageHolder? =
        getConversationListView()
            ?.children
            ?.mapNotNull { view ->
                (view.tag as? ComposeMessageHolder)
            }
            ?.firstOrNull { messageHolder ->
                messageHolder.emojiReactionGroup?.firstReactionButton != null
            }

    private fun pollAnchorLocation(view: View, originalLocation: IntArray) {
        val newLocation = view.getLocation()
        if (!newLocation.contentEquals(originalLocation)) {
            dismiss()
        } else {
            handler.postDelayed(
                { pollAnchorLocation(view, originalLocation) },
                ANCHOR_MOVEMENT_POLL_INTERVAL
            )
        }
    }

    fun onDestroy() {
        dismiss()
    }

    companion object {
        private const val POPUP_DELAY = 1000L
        private const val ANCHOR_MOVEMENT_POLL_INTERVAL = 750L
        private const val POPUP_TIMEOUT = 8000
        private const val MAX_TIMES_SHOWN = 2
        private val VALID_VERTICAL_SCREEN_RANGE = 0.05..0.98
    }
}
