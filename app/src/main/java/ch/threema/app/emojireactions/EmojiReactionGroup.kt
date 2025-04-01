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
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.emojireactions.EmojiReactionsButton.OnEmojiReactionButtonClickListener
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.data.models.EmojiReactionData
import ch.threema.storage.models.AbstractMessageModel
import kotlin.math.roundToInt

data class ButtonInfo(
    val emojiSequence: String,
    val count: Int,
    val isChecked: Boolean
)

class EmojiReactionGroup : LinearLayoutCompat, OnEmojiReactionButtonClickListener,
    SelectEmojiButton.OnSelectEmojiButtonClickListener,
    MoreReactionsButton.OnMoreReactionsButtonClickListener {
    private var messageModel: AbstractMessageModel? = null
    private var buttonInfoList: MutableList<ButtonInfo> = ArrayList()
    private var bubbleView: View? = null
    private var messageReceiver: MessageReceiver<*>? = null
    var onEmojiReactionGroupClickListener: OnEmojiReactionGroupClickListener? = null
    val userService = ThreemaApplication.requireServiceManager().userService
    var reactions: List<EmojiReactionData> = ArrayList()
    var firstReactionButton: View? = null
        private set
    private var listViewWidth = -1
    private var messageBubbleWidth = -1
    private val buttonWidth = resources.getDimensionPixelSize(R.dimen.emojireactions_width)
    private val buttonWidthWithCount =
        resources.getDimensionPixelSize(R.dimen.emojireactions_width_with_count)
    private lateinit var contextThemeWrapper: ContextThemeWrapper

    constructor(context: Context) : super(
        context
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
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
        setWillNotDraw(false)

        orientation = HORIZONTAL
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

        contextThemeWrapper = getContextThemeWrapper(context)
    }

    fun setMessageModel(
        messageReceiver: MessageReceiver<*>,
        messageModel: AbstractMessageModel,
        reactions: List<EmojiReactionData>
    ) {
        this.messageModel = messageModel
        this.reactions = reactions
        this.messageReceiver = messageReceiver

        val newBubbleView: View = (parent as ViewGroup).findViewById(R.id.message_block)
        if (newBubbleView != bubbleView) {
            this.bubbleView = newBubbleView
            this.messageBubbleWidth = getBubbleWidth()
            if (this.listViewWidth == -1) {
                this.listViewWidth = getListViewWidth()
            }
        }

        drawButtons()
    }

    private fun drawButtons() {
        synchronized(buttonInfoList) {
            if (messageBubbleWidth == -1) {
                return
            }

            if (listViewWidth == -1) {
                return
            }

            val newButtonInfoList: MutableList<ButtonInfo> = ArrayList()

            if (reactions.isNotEmpty()) {
                val grouped = reactions.groupingBy { it.emojiSequence }.eachCount()

                for ((key, count) in grouped) {
                    newButtonInfoList.add(
                        ButtonInfo(
                            emojiSequence = key,
                            count,
                            isChecked = reactions.any { reaction -> reaction.emojiSequence == key && reaction.senderIdentity == userService.identity })
                    )
                }
            }

            if (newButtonInfoList != buttonInfoList) {
                var allowedWidth: Int = if (messageBubbleWidth * 2 > listViewWidth) {
                    // When the message occupies more than 50% of the screen, the emojis occupy 100% of the message and can overflow to cover 60% of the screen.
                    (listViewWidth * 0.6).roundToInt()
                } else {
                    // When the message is very short (10% to 20%), the line of emojis can only occupy 50% of the whole screen. (The bubbles can go a little bit outside the message).
                    (listViewWidth * 0.5).roundToInt()
                }
                // deduct width of mandatory select button
                allowedWidth -= buttonWidth

                removeAllViewsInLayout()

                var firstButton: View? = null
                var usedWidth = 0
                for ((index, buttonInfo) in newButtonInfoList.withIndex()) {
                    val emojiReactionButton = createEmojiReactionButton(buttonInfo)
                    usedWidth += if (buttonInfo.count > 1) {
                        buttonWidthWithCount
                    } else {
                        buttonWidth
                    }

                    if (usedWidth >= allowedWidth && index < newButtonInfoList.size - 1) {
                        createAndAddMoreReactionsButton(newButtonInfoList.size - index)
                        break
                    }

                    if (firstButton == null) {
                        firstButton = emojiReactionButton
                    }

                    emojiReactionButton.onEmojiReactionButtonClickListener = this
                    addViewInLayout(emojiReactionButton, -1, emojiReactionButton.layoutParams)
                }
                firstReactionButton = firstButton

                messageReceiver
                    ?.takeIf { it.emojiReactionSupport != MessageReceiver.Reactions_NONE }
                    ?.takeIf { newButtonInfoList.isNotEmpty() }
                    ?.let {
                        createAndAddSelectEmojiButton()
                    }

                buttonInfoList = newButtonInfoList

                requestLayout()
                parent.requestLayout()
            }
        }
    }

    private fun createEmojiReactionButton(buttonInfo: ButtonInfo): EmojiReactionsButton {
        val emojiReactionsButton = EmojiReactionsButton(contextThemeWrapper)
        emojiReactionsButton.setButtonData(buttonInfo.emojiSequence, buttonInfo.count)
        emojiReactionsButton.isChecked = buttonInfo.isChecked
        return emojiReactionsButton
    }

    private fun createAndAddMoreReactionsButton(remainingCount: Int) {
        val moreReactionsButton = MoreReactionsButton(contextThemeWrapper)
        moreReactionsButton.setButtonData("+$remainingCount")
        moreReactionsButton.isChecked = false
        moreReactionsButton.onMoreReactionsButtonClickListener = this
        addViewInLayout(moreReactionsButton, -1, moreReactionsButton.layoutParams)
    }

    private fun createAndAddSelectEmojiButton() {
        val selectEmojiButton = SelectEmojiButton(contextThemeWrapper)
        selectEmojiButton.isChecked = false
        selectEmojiButton.onSelectEmojiButtonClickListener = this
        addViewInLayout(selectEmojiButton, -1, selectEmojiButton.layoutParams)
    }

    private fun getContextThemeWrapper(context: Context): ContextThemeWrapper {
        return ContextThemeWrapper(
            context,
            R.style.Threema_EmojiReactions_Button
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (parent == null) {
            return
        }

        listViewWidth = getListViewWidth()
        messageBubbleWidth = getBubbleWidth()

        drawButtons()
    }

    private fun getListViewWidth(): Int {
        val listView = parent?.parent as? View
        return listView?.width ?: -1
    }

    private fun getBubbleWidth(): Int {
        return bubbleView?.width ?: -1
    }

    interface OnEmojiReactionGroupClickListener {
        fun onEmojiReactionClick(messageModel: AbstractMessageModel?, emojiSequence: String?)
        fun onEmojiReactionLongClick(messageModel: AbstractMessageModel?, emojiSequence: String?)
        fun onSelectButtonClick(messageModel: AbstractMessageModel?)
        fun onMoreReactionsButtonClick(messageModel: AbstractMessageModel?)
    }

    override fun onClick(emojiSequence: String?) {
        onEmojiReactionGroupClickListener?.onEmojiReactionClick(messageModel, emojiSequence)
    }

    override fun onLongClick(emojiSequence: String?) {
        onEmojiReactionGroupClickListener?.onEmojiReactionLongClick(messageModel, emojiSequence)
    }

    override fun onSelectButtonClick() {
        onEmojiReactionGroupClickListener?.onSelectButtonClick(messageModel)
    }

    override fun onMoreReactionsButtonClick() {
        onEmojiReactionGroupClickListener?.onMoreReactionsButtonClick(messageModel)
    }

    override fun onMoreReactionsButtonLongClick() {
        onEmojiReactionGroupClickListener?.onEmojiReactionLongClick(messageModel, null)
    }
}
