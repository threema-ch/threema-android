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

package ch.threema.app.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import ch.threema.app.R
import ch.threema.app.emojis.EmojiConversationTextView
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LinkifyUtil
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

class MessageBubbleView : FrameLayout {
    private lateinit var textView: EmojiConversationTextView

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_message_bubble, this)
    }

    fun show(messageModel: AbstractMessageModel) {
        @LayoutRes val footerLayout: Int
        @ColorInt val color: Int

        if (messageModel.isOutbox) {
            // send
            color = ConfigUtils.getColorFromAttribute(context, R.attr.colorSecondaryContainer)
            footerLayout = R.layout.conversation_bubble_footer_send
        } else {
            // recv
            color = resources.getColor(R.color.bubble_receive)
            footerLayout = R.layout.conversation_bubble_footer_recv
        }

        findViewById<MaterialCardView>(R.id.card_view).setCardBackgroundColor(color)

        val footerView: View = LayoutInflater.from(context).inflate(footerLayout, null)
        val footer = findViewById<FrameLayout>(R.id.footer)

        footer.removeAllViews()
        footer.addView(footerView)
        textView = findViewById(R.id.text_view)

        // display date
        val s: CharSequence? = MessageUtil.getDisplayDate(context, messageModel, true)
        (footerView.findViewById<TextView>(R.id.date_view)!!).text = s ?: ""

        // display message status
        StateBitmapUtil.getInstance()?.setStateDrawable(context, messageModel, footerView.findViewById(R.id.delivered_indicator), true)

        // mock a composemessageholder
        val holder = ComposeMessageHolder()
        holder.groupAckContainer = footerView.findViewById(R.id.groupack_container)
        holder.groupAckThumbsUpCount = footerView.findViewById(R.id.groupack_thumbsup_count)
        holder.groupAckThumbsDownCount = footerView.findViewById(R.id.groupack_thumbsdown_count)
        holder.groupAckThumbsUpImage = footerView.findViewById(R.id.groupack_thumbsup)
        holder.groupAckThumbsDownImage = footerView.findViewById(R.id.groupack_thumbsdown)
        holder.deliveredIndicator = footerView.findViewById(R.id.delivered_indicator)
        holder.editedText = footerView.findViewById(R.id.edited_text)

        StateBitmapUtil.getInstance()!!.setGroupAckCount(messageModel, holder)
        messageModel.editedAt?.let { holder.editedText.visibility = VISIBLE }

        setText(messageModel)
    }

    fun setText(messageModel: AbstractMessageModel) {
        textView.text = QuoteUtil.getMessageBody(messageModel, false)
        invalidate()
        requestLayout()
    }

    fun linkifyText(activity: AppCompatActivity, messageModel: AbstractMessageModel, ignoreMarkup: Boolean) {
        textView.setIgnoreMarkup(ignoreMarkup)
        setText(messageModel)
        if (!ignoreMarkup) {
            LinkifyUtil.getInstance().linkify(null, activity, textView, messageModel, true, false, null)
        }
    }

    fun linkifyText(fragment: Fragment, messageModel: AbstractMessageModel, ignoreMarkup: Boolean) {
        textView.setIgnoreMarkup(ignoreMarkup)
        if (!ignoreMarkup) {
            LinkifyUtil.getInstance().linkify(fragment, null, textView, messageModel, true, false, null)
        }
    }

    fun increaseTextSizeByDp(dp: Int) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getTextSizeDp() + dp + 0f)
    }

    fun getTextSizeDp(): Int {
        return (textView.textSize / resources.displayMetrics.density).roundToInt()
    }

    fun setCustomSelectionActionModeCallback(cb: ActionMode.Callback) {
        textView.customSelectionActionModeCallback = cb
    }
}
