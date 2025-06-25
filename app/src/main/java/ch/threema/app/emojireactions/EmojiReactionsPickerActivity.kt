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

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiPicker
import ch.threema.app.emojis.EmojiService
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.ui.RootViewDeferringInsetsCallback
import ch.threema.app.ui.SingleToast
import ch.threema.app.ui.TranslateDeferringInsetsAnimationCallback
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.getCurrentInsets
import ch.threema.app.utils.logScreenVisibility
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
    init {
        logScreenVisibility(logger)
    }

    private var emojiService: EmojiService = ThreemaApplication.requireServiceManager().emojiService
    private var messageService = ThreemaApplication.requireServiceManager().messageService
    private var messageModel: AbstractMessageModel? = null
    private lateinit var parentLayout: LinearLayout
    private var emojiPicker: EmojiPicker? = null

    override fun getLayoutResource(): Int = R.layout.activity_emojireactions_picker

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

        emojiPicker = (findViewById<View>(R.id.emoji_stub) as ViewStub).inflate() as EmojiPicker
        emojiPicker!!.setEmojiKeyListener(this)

        try {
            val tag = "emoji-picker-activity"

            val deferringInsetsListener = RootViewDeferringInsetsCallback(
                tag = tag,
                emojiPicker = emojiPicker,
                threemaToolbarActivity = this,
                persistentInsetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )

            ViewCompat.setWindowInsetsAnimationCallback(parentLayout, deferringInsetsListener)
            ViewCompat.setOnApplyWindowInsetsListener(parentLayout, deferringInsetsListener)

            emojiPicker?.let { emojiPicker ->
                ViewCompat.setWindowInsetsAnimationCallback(
                    emojiPicker,
                    TranslateDeferringInsetsAnimationCallback(
                        tag,
                        emojiPicker,
                        emojiPicker,
                        WindowInsetsCompat.Type.systemBars(),
                        WindowInsetsCompat.Type.ime(),
                        WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
                    ),
                )
            }
        } catch (e: NullPointerException) {
            logger.error("Exception", e)
        }

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
                                    picker.init(this@EmojiReactionsPickerActivity, emojiService, false, emojiReactions)
                                    emojiPicker?.show(loadStoredSoftKeyboardHeight())
                                    removeVerticalInsetsFromInsetPaddingContainer()
                                }
                            }
                        }
                }
            }
        }
        return true
    }

    /**
     * If the emoji picker is shown, we have to make sure that no vertical padding insets are applied.
     * The emoji picker has to handle the vertical insets internally.
     *
     * This will remove any vertical padding of [parentLayout] while still respecting the horizontal insets.
     */
    private fun removeVerticalInsetsFromInsetPaddingContainer() {
        val insets: Insets = this.getCurrentInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        parentLayout.setPadding(insets.left, 0, insets.right, 0)
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
        if (emojiCodeString == null) {
            logger.debug("No emoji selected")
            finish()
            return
        }

        val messageReceiver: MessageReceiver<AbstractMessageModel> = messageService.getMessageReceiver(messageModel)

        // If this is a group message, we have to check if we are still an active member of the group
        if (messageReceiver is GroupMessageReceiver) {
            messageReceiver.groupModel?.data?.value?.let { currentGroupModelData ->
                if (!currentGroupModelData.isMember) {
                    SingleToast.getInstance().showLongText(getString(R.string.you_are_not_a_member_of_this_group))
                    finish()
                    return
                }
            }
        }

        // add selected reaction
        CoroutineScope(Dispatchers.Default).launch {
            messageService.sendEmojiReaction(
                messageModel as AbstractMessageModel,
                emojiCodeString,
                messageReceiver,
                false,
            )
        }

        this.finish()
    }
}
