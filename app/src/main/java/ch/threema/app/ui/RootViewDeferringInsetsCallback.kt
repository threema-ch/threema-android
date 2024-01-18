/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiPicker
import ch.threema.app.services.PreferenceService
import ch.threema.base.utils.LoggingUtil

class RootViewDeferringInsetsCallback(private val emojiPicker: EmojiPicker?, private val threemaToolbarActivity: ThreemaToolbarActivity):
    WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE), androidx.core.view.OnApplyWindowInsetsListener {

    private companion object {
        private val logger = LoggingUtil.getThreemaLogger("RootViewDeferringInsetsCallback")
    }

    private var view: View? = null
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var deferredInsets = false
    private var disableAnimation = false
    private val preferenceService: PreferenceService? = ThreemaApplication.getServiceManager()?.preferenceService

    override fun onApplyWindowInsets(
        v: View,
        windowInsets: WindowInsetsCompat
    ): WindowInsetsCompat {
        logger.debug(
            "onApplyWindowInsets deferred = {}, disabledAnim = {}",
            deferredInsets,
            disableAnimation
        )
        view = v
        lastWindowInsets = windowInsets
        val types =
            if (deferredInsets || disableAnimation) WindowInsetsCompat.Type.systemBars() else WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        val typeInsets = windowInsets.getInsets(types)
        val notchInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

        v.setPadding(typeInsets.left + notchInsets.left, typeInsets.top, typeInsets.right + notchInsets.right, typeInsets.bottom)
        val attachedActivity: ThreemaToolbarActivity = threemaToolbarActivity
        if (windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
            attachedActivity.onSoftKeyboardOpened(
                windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom - windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                ).bottom
            )
        } else {
            attachedActivity.onSoftKeyboardClosed()
        }
        return WindowInsetsCompat.CONSUMED
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
       logger.debug("onPrepare, typeMask = {}, emojiPicker {} emojiSearch {}, lastInset {}",
            animation.typeMask,
            emojiPicker?.isShown,
            emojiPicker?.isEmojiSearchShown,
            lastWindowInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom
        )
        if (animation.typeMask and WindowInsetsCompat.Type.ime() == WindowInsetsCompat.Type.ime()) {
            deferredInsets = true
        }
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat?>
    ): WindowInsetsCompat {
        logger.debug("onProgress, disabled {}", disableAnimation)

        // consume insets if emojipicker showing or hiding is in progress to prevent flicker
        return if (preferenceService?.emojiStyle == PreferenceService.EmojiStyle_DEFAULT) {
            if (disableAnimation) WindowInsetsCompat.CONSUMED else insets
        } else insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        logger.debug("onEnd")
        if (lastWindowInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            emojiPicker?.let {
                if (it.isShown && !it.isEmojiSearchShown) {
                    it.hide()
                }
            }
        }
        disableAnimation = false
        if (animation.typeMask and WindowInsetsCompat.Type.ime() == WindowInsetsCompat.Type.ime()) {
            if (deferredInsets) {
                deferredInsets = false
                view?.let {view ->
                    lastWindowInsets?.let {lastWindowInsets ->
                        ViewCompat.dispatchApplyWindowInsets(view, lastWindowInsets)
                    }
                }
            }
        }
    }

    fun setDisableAnimation(disableAnimation: Boolean) {
        logger.debug("setDisableAnimation {}", disableAnimation)
        this.disableAnimation = disableAnimation
    }
}
