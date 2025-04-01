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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiPicker
import ch.threema.app.emojis.EmojiService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.storage.models.AbstractMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("EmojiReactionPickerActivity")

class EmojiReactionsPickerActivity : ThreemaToolbarActivity(), EmojiPicker.EmojiKeyListener {
    private var emojiService: EmojiService = ThreemaApplication.requireServiceManager().emojiService
    private var messageService = ThreemaApplication.requireServiceManager().messageService
    private var messageModel: AbstractMessageModel? = null
    private lateinit var parentLayout: LinearLayout
    private var emojiPicker: EmojiPicker? = null

    override fun getLayoutResource(): Int {
        return R.layout.activity_emojireactions_picker
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        messageModel = IntentDataUtil.getAbstractMessageModel(intent, messageService)
        if (messageModel == null) {
            finish()
            return false
        }

        if (!super.initActivity(savedInstanceState)) {
            finish()
            return false
        }

        parentLayout = findViewById(R.id.parent_layout)
        parentLayout.setOnClickListener { _: View? ->
            finish()
        }

        @Suppress("DEPRECATION")
        window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_collapsed)

        emojiPicker = (findViewById<View>(R.id.emoji_stub) as ViewStub).inflate() as EmojiPicker
        emojiPicker?.setEmojiKeyListener(this)

        lifecycleScope.launch {
            val emojiReactionsRepository: EmojiReactionsRepository =
                ThreemaApplication.requireServiceManager().modelRepositories.emojiReaction
            val emojiReactionsModel: EmojiReactionsModel? =
                emojiReactionsRepository.getReactionsByMessage(messageModel!!)

            emojiReactionsModel?.let { reactionModel ->
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    reactionModel.data.firstOrNull()
                        .let { emojiReactions: List<EmojiReactionData>? ->
                            emojiPicker?.let { picker ->
                                // inflate emoji picker
                                withContext(Dispatchers.Main) {
                                    picker.init(emojiService, false, emojiReactions)
                                    showEmojiPicker()
                                }
                            }
                        }
                }
            }
        }
        return true
    }

    private fun showEmojiPicker() {
        emojiPicker?.show(loadStoredSoftKeyboardHeight())
    }

    override fun finish() {
        try {
            super.finish()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        } catch (ignored: Exception) {
            // ignored
        }
    }

    override fun onBackspaceClick() {
        // backspace key is disabled
    }

    override fun onEmojiClick(emojiCodeString: String?) {
        if (emojiCodeString != null) {
            // add selected reaction
            val messageReceiver = messageService.getMessageReceiver(messageModel)

            CoroutineScope(Dispatchers.Default).launch {
                messageService.sendEmojiReaction(
                    messageModel as AbstractMessageModel,
                    emojiCodeString,
                    messageReceiver,
                    false
                )
            }
        } else {
            logger.debug("No emoji selected")
        }
        this.finish()
    }

    override fun onShowPicker() {
        // not used
    }
}
