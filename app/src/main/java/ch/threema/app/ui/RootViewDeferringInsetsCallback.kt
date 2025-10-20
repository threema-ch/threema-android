/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiPicker
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("RootViewDeferringInsetsCallback")

class RootViewDeferringInsetsCallback(
    private val tag: String,
    private val emojiPicker: EmojiPicker?,
    private val threemaToolbarActivity: ThreemaToolbarActivity?,
    private val persistentInsetTypes: Int,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE),
    androidx.core.view.OnApplyWindowInsetsListener {

    private var mView: View? = null
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var ignoreIME = false

    /**
     *  If set to `true`, a bottom padding of 0 will be applied in [onApplyWindowInsets]. Will be reset in the next [onEnd] call.
     */
    @JvmField
    var openingEmojiPicker: Boolean = false

    var isEnabled: Boolean = true
        set(value) {
            field = value
            logger.debug("{} Callback enabled: {}", tag, value)
        }

    override fun onApplyWindowInsets(view: View, windowInsets: WindowInsetsCompat): WindowInsetsCompat {
        if (!isEnabled) {
            return windowInsets
        }

        logger.debug("{} onApplyWindowInsets: ignoring IME insets = {}, openingEmojiPicker = {}", tag, ignoreIME, openingEmojiPicker)
        mView = view
        lastWindowInsets = windowInsets

        // Decide whether we want to defer (ignore) the IME insets for now
        // After any inset animation has ended, this method will be called again with the final insets and deferredInsets = false
        val types: Int = if (ignoreIME) {
            persistentInsetTypes
        } else {
            persistentInsetTypes or WindowInsetsCompat.Type.ime()
        }
        val insets: Insets = windowInsets.getInsets(types)

        val effectiveBottomPadding: Int = when {
            openingEmojiPicker -> 0
            emojiPicker?.isShown == true -> {
                // The emoji picker is currently visible.
                if (ignoreIME) {
                    // If we are currently ignoring the IME insets, we have to set no bottom padding.
                    0
                } else {
                    // Otherwise we have to set the height of the IME insets as bottom padding.
                    // Normally this would not be necessary, as we do not show the keyboard and the emoji-picker at the same time.
                    // But if the user uses the emoji search from the picker, we have to display both and therefore apply the
                    // keyboard height as bottom padding.
                    windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                }
            }

            else -> insets.bottom
        }

        // Apply the effectively needed padding to the desired view
        logger.debug("{} onApplyWindowInsets setPadding({}, {}, {}, {})", tag, insets.left, 0, insets.right, effectiveBottomPadding)
        view.setPadding(insets.left, 0, insets.right, effectiveBottomPadding)

        // Inform the calling activity about the keyboard state
        if (threemaToolbarActivity != null) {
            if (windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
                threemaToolbarActivity.onSoftKeyboardOpened(windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else {
                threemaToolbarActivity.onSoftKeyboardClosed()
            }
        }
        return WindowInsetsCompat.CONSUMED
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (!isEnabled) {
            return
        }

        logger.debug(
            "{} onPrepare: typeMask = {}, emojiPicker {} emojiSearch {}",
            tag,
            animation.typeMask,
            emojiPicker?.isShown,
            emojiPicker?.isEmojiSearchShown,
        )
        if (animation.typeMask and WindowInsetsCompat.Type.ime() == WindowInsetsCompat.Type.ime()) {
            // An animation of the IME insets has is about to start, so we have to turn on our flag
            ignoreIME = true
        }
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat?>,
    ): WindowInsetsCompat {
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (!isEnabled) {
            return
        }

        logger.debug("{} onEnd", tag)

        // If the keyboard was animated over an opened emoji picker, we want to close this emoji picker behind the expanded keyboard:
        if (lastWindowInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            emojiPicker?.let {
                if (it.isShown && !it.isEmojiSearchShown) {
                    logger.debug("{} onEnd: closing emoji picker because the keyboard was opened", tag)
                    it.hide()
                }
            }
        }
        openingEmojiPicker = false
        if (ignoreIME && animation.typeMask and WindowInsetsCompat.Type.ime() == WindowInsetsCompat.Type.ime()) {
            ignoreIME = false
            mView?.let { view ->
                lastWindowInsets?.let { lastWindowInsets ->
                    logger.debug("{} onEnd: Dispatching deferred window insets", tag)
                    ViewCompat.dispatchApplyWindowInsets(view, lastWindowInsets)
                }
            }
        }
    }
}
