/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.emojis.EmojiItemView
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AnimationUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.ViewUtil
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel

private const val FAKE_DISABLE_ALPHA = 0.2f

class EmojiReactionsPopup(
    val context: Context,
    private val parentView: View,
    val fragmentManager: FragmentManager,
    private val isSendingReactionsAllowed: Boolean,
    private val shouldHideUnsupportedReactions: Boolean,
) :
    PopupWindow(context), View.OnClickListener {

    private val addReactionButton: ImageView
    private var emojiReactionsPopupListener: EmojiReactionsPopupListener? = null
    private val popupHeight =
        2 * context.resources.getDimensionPixelSize(R.dimen.reaction_popup_content_margin) +
            context.resources.getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_bottom) +
            context.resources.getDimensionPixelSize(R.dimen.reaction_popup_emoji_size)
    private val popupHorizontalOffset =
        context.resources.getDimensionPixelSize(R.dimen.reaction_popup_content_margin_horizontal)
    private var messageModel: AbstractMessageModel? = null
    private val emojiReactionsRepository: EmojiReactionsRepository =
        ThreemaApplication.requireServiceManager().modelRepositories.emojiReaction
    private val userService: UserService = ThreemaApplication.requireServiceManager().userService
    private val emojiService = ThreemaApplication.requireServiceManager().emojiService
    private val selectedBackgroundColor = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.shape_emoji_popup_selected_background,
        null
    )
    private val backgroundColor =
        ResourcesCompat.getColor(context.resources, android.R.color.transparent, null)
    private val topReactions = arrayOf(
        ReactionEntry(R.id.top_0, EmojiUtil.THUMBS_UP_SEQUENCE),
        ReactionEntry(R.id.top_1, EmojiUtil.THUMBS_DOWN_SEQUENCE),
        ReactionEntry(R.id.top_2, EmojiUtil.HEART_SEQUENCE),
        ReactionEntry(R.id.top_3, EmojiUtil.TEARS_OF_JOY_SEQUENCE),
        ReactionEntry(R.id.top_4, EmojiUtil.CRYING_SEQUENCE),
        ReactionEntry(R.id.top_5, EmojiUtil.FOLDED_HANDS_SEQUENCE),
    )

    private val contactService: ContactService by lazy { ThreemaApplication.requireServiceManager().contactService }

    init {
        val layoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        contentView =
            layoutInflater.inflate(R.layout.popup_emojireactions, null, true) as FrameLayout

        setupTopReactions()

        addReactionButton = contentView.findViewById(R.id.add_reaction)
        addReactionButton.tag = topReactions.size
        addReactionButton.setOnClickListener(this)
        if (!isSendingReactionsAllowed) {
            if (ConfigUtils.canSendEmojiReactions() && !shouldHideUnsupportedReactions) {
                // V2 clients: display implausible buttons as disabled but still clickable
                addReactionButton.alpha = FAKE_DISABLE_ALPHA
            } else {
                // V1 clients, or gateway chat: do not display implausible buttons
                addReactionButton.isVisible = false
            }
        }

        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        width = FrameLayout.LayoutParams.WRAP_CONTENT
        height = FrameLayout.LayoutParams.WRAP_CONTENT

        setBackgroundDrawable(BitmapDrawable())
        animationStyle = 0
        isOutsideTouchable = true
        isTouchable = true
        isFocusable = true
        ViewUtil.setTouchModal(this, false)
    }

    private fun setupTopReactions() {
        topReactions.forEachIndexed { index, topReaction ->
            val emojiItemView: EmojiItemView = contentView.findViewById(topReaction.resourceId)

            applyEmojiDiversity(topReaction)
            emojiItemView.tag = index
            emojiItemView.setOnClickListener(this)
            emojiItemView.setEmoji(topReaction.emojiSequence, false, 0)

            if (isDisabledOrHiddenButton(index)) {
                if (ConfigUtils.canSendEmojiReactions() && !shouldHideUnsupportedReactions) {
                    // V2 clients: display implausible buttons as disabled but still clickable
                    emojiItemView.alpha = FAKE_DISABLE_ALPHA
                } else {
                    // V1 clients: do not display implausible buttons
                    emojiItemView.isVisible = false
                }
            }

            topReaction.emojiItemView = emojiItemView
        }
    }

    private fun applyEmojiDiversity(topReaction: ReactionEntry) {
        if (isSendingReactionsAllowed) {
            val value = emojiService.getPreferredDiversity(topReaction.emojiSequence)
            if (value != topReaction.emojiSequence) {
                topReaction.emojiSequence = value
            }
        }
    }

    fun show(
        originView: View,
        messageModel: AbstractMessageModel?
    ) {
        if (messageModel == null) {
            return
        }

        this.messageModel = messageModel
        isDismissing = false

        val horizontalOffset = if (messageModel.isOutbox) 0 else this.popupHorizontalOffset

        val originLocation = intArrayOf(0, 0)
        originView.getLocationInWindow(originLocation)
        showAtLocation(
            parentView,
            Gravity.LEFT or Gravity.TOP,
            originLocation[0] + horizontalOffset,
            originLocation[1] - this.popupHeight
        )

        val emojiReactionsModel: EmojiReactionsModel? =
            emojiReactionsRepository.getReactionsByMessage(messageModel)

        contentView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                AnimationUtil.popupAnimateIn(contentView)

                var animationDelay = 10
                val animationDelayStep = 60

                for (topReaction in topReactions) {
                    if (topReaction.emojiItemView != null) {
                        AnimationUtil.bubbleAnimate(
                            topReaction.emojiItemView,
                            animationDelayStep.let { animationDelay += it; animationDelay })

                        if (hasUserReacted(topReaction, emojiReactionsModel)) {
                            topReaction.emojiItemView?.background = selectedBackgroundColor
                        } else {
                            topReaction.emojiItemView?.setBackgroundColor(backgroundColor)
                        }
                    }
                }
                AnimationUtil.bubbleAnimate(addReactionButton, animationDelay)
            }
        })
    }

    private fun hasUserReacted(
        topReaction: ReactionEntry,
        emojiReactionsModel: EmojiReactionsModel?
    ): Boolean {
        val reactionList = emojiReactionsModel?.data?.value
        return reactionList?.any { reaction ->
            reaction.emojiSequence == topReaction.emojiSequence && reaction.senderIdentity == userService.identity
        } ?: false
    }

    /**
     * Check if the button with the supplied index is unsupported and
     * should therefore be fake-disabled or hidden
     */
    private fun isDisabledOrHiddenButton(index: Int): Boolean =
        !isSendingReactionsAllowed && index >= 2

    override fun dismiss() {
        if (isDismissing) {
            return
        }
        isDismissing = true
        AnimationUtil.popupAnimateOut(contentView) {
            super.dismiss()
        }
    }

    fun setListener(listener: EmojiReactionsPopupListener?) {
        this.emojiReactionsPopupListener = listener
    }

    override fun onClick(v: View) {
        emojiReactionsPopupListener?.let { listener ->
            this.messageModel?.let { message ->
                if (isDisabledOrHiddenButton(v.tag as Int)) {
                    onDisabledButtonClicked()
                    return
                } else {
                    if (v.id == addReactionButton.id) {
                        listener.onAddReactionClicked(message)
                    } else {
                        if (v is EmojiItemView) {
                            if (isSendingReactionsAllowed || v.background != selectedBackgroundColor) {
                                listener.onTopReactionClicked(message, v.emoji)
                            } else {
                                // A reaction that cannot be sent was clicked (e.g. Thumbs up was clicked,
                                // while a thumbs up is already sent, but only ack/dec are supported)
                                onImpossibleReactionClicked()
                                return
                            }
                        }
                    }
                }
            }
        }
        dismiss()
    }

    private fun onDisabledButtonClicked() {
        val body = if (messageModel is GroupMessageModel) {
            context.getString(R.string.emoji_reactions_unavailable_group_body)
        } else {
            messageModel.getDisplayNameOrNickname()
                ?.let { name -> context.getString(R.string.emoji_reactions_unavailable_body, name) }
        }

        createAlertDialogIfBodySet(
            R.string.emoji_reactions_unavailable_title,
            body
        )?.show(fragmentManager, "dis")
    }

    private fun onImpossibleReactionClicked() {
        val body = if (ConfigUtils.canSendEmojiReactions()) {
            if (messageModel is GroupMessageModel) {
                context.getString(R.string.emoji_reactions_cannot_remove_group_body)
            } else {
                messageModel.getDisplayNameOrNickname()
                    ?.let { name ->
                        context.getString(
                            R.string.emoji_reactions_cannot_remove_body,
                            name
                        )
                    }
            }
        } else {
            context.getString(R.string.emoji_reactions_cannot_remove_v1_body)
        }

        createAlertDialogIfBodySet(
            R.string.emoji_reactions_cannot_remove_title,
            body
        )?.show(fragmentManager, "imp")
    }

    private fun createAlertDialogIfBodySet(
        @StringRes titleResId: Int,
        body: String?
    ): SimpleStringAlertDialog? {
        return body?.let {
            SimpleStringAlertDialog.newInstance(titleResId, it)
        }
    }

    private fun AbstractMessageModel?.getDisplayNameOrNickname(): String? {
        return this?.let { NameUtil.getDisplayNameOrNickname(context, it, contactService) }
    }

    interface EmojiReactionsPopupListener {
        fun onTopReactionClicked(messageModel: AbstractMessageModel, emojiSequence: String)
        fun onAddReactionClicked(messageModel: AbstractMessageModel)
    }

    companion object {
        private var isDismissing = false
    }

    private class ReactionEntry(val resourceId: Int, var emojiSequence: String) {
        var emojiItemView: EmojiItemView? = null
    }
}
