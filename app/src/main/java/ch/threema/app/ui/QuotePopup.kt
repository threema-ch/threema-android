/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager.BadTokenException
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import ch.threema.app.R
import ch.threema.app.cache.ThumbnailCache
import ch.threema.app.emojis.EmojiMarkupUtil
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.QuoteUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

private val logger = getThreemaLogger("QuotePopup")

@SuppressLint("InflateParams")
class QuotePopup(
    private val context: Context,
    private val contactService: ContactService,
    private val userService: UserService,
    private val fileService: FileService,
    private val thumbnailCache: ThumbnailCache<*>,
) : MovingPopupWindow(context) {
    private val quoteTextView: TextView
    private val quoteIdentityView: TextView
    private val quoteThumbnail: ImageView
    private val quoteTypeImage: ImageView
    private val quoteBar: View
    private val popupLayout: MaterialCardView
    private var quotePopupListener: QuotePopupListener? = null

    class QuoteInfo {
        var quoteText: String? = null
        var quoteIdentity: Identity? = null
        var messageModel: AbstractMessageModel? = null
    }

    val quoteInfo = QuoteInfo()

    init {
        popupLayout = LayoutInflater.from(context)
            .inflate(R.layout.popup_quote, null, false) as MaterialCardView
        quoteTextView = popupLayout.findViewById(R.id.quote_text_view)
        quoteIdentityView = popupLayout.findViewById(R.id.quote_id_view)
        quoteBar = popupLayout.findViewById(R.id.quote_bar)
        quoteThumbnail = popupLayout.findViewById(R.id.quote_thumbnail)
        quoteTypeImage = popupLayout.findViewById(R.id.quote_type_image)
        val quoteCloseButton = popupLayout.findViewById<ImageView>(R.id.quote_panel_close_button)
        quoteCloseButton.setOnClickListener { dismiss() }

        contentView = popupLayout
        inputMethodMode = INPUT_METHOD_NEEDED
        animationStyle = R.style.Threema_Animation_QuotePopup
        isFocusable = false
        isTouchable = true
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        setWindowLayoutMode(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        height = 1
    }

    fun show(
        activity: Activity,
        editText: ComposeEditText,
        textInputLayout: TextInputLayout,
        messageModel: AbstractMessageModel?,
        identity: Identity?,
        barColor: ColorStateList,
        listener: QuotePopupListener?,
    ) {
        this.quotePopupListener = listener

        super.show(activity, textInputLayout)

        popupLayout.setCardBackgroundColor(textInputLayout.boxBackgroundColor)
        val coordinates = ConfigUtils.getPopupWindowPositionAboveAnchor(activity, textInputLayout)
        val popupX = coordinates[0]
        val popupY = coordinates[1]
        val viewableSpaceHeight =
            coordinates[2] - context.resources.getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_bottom)
        this.width = editText.width
        this.height = viewableSpaceHeight

        try {
            showAtLocation(editText, Gravity.LEFT or Gravity.BOTTOM, popupX, popupY)
            adjustCornersToOpenState(textInputLayout, 20)
            contentView.viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        contentView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                        listener?.onHeightSet(popupLayout.measuredHeight)
                    }
                },
            )
            anchorView?.let {
                ViewCompat.setWindowInsetsAnimationCallback(it, windowInsetsAnimationCallback)
                it.addOnLayoutChangeListener(onLayoutChangeListener)
            }
            adjustCornersToOpenState(textInputLayout, 200)
        } catch (e: BadTokenException) {
            //
        }
        quoteInfo.messageModel = messageModel
        quoteInfo.quoteText = QuoteUtil.getMessageBody(quoteInfo.messageModel, true)
        quoteInfo.quoteIdentity = identity
        quoteIdentityView.text =
            NameUtil.getQuoteName(quoteInfo.quoteIdentity, contactService, userService)
        quoteBar.backgroundTintList = barColor
        quoteTextView.text = EmojiMarkupUtil.getInstance()
            .addTextSpans(activity, quoteInfo.quoteText, quoteTextView, false, false)
        quoteThumbnail.visibility = View.GONE
        quoteTypeImage.visibility = View.GONE

        try {
            val thumbnail = fileService.getMessageThumbnailBitmap(
                messageModel,
                thumbnailCache,
            )
            if (thumbnail != null) {
                quoteThumbnail.setImageBitmap(thumbnail)
                quoteThumbnail.visibility = View.VISIBLE
            }
        } catch (ignore: Exception) {
        }

        val messageViewElement = MessageUtil.getViewElement(context, messageModel)
        if (messageViewElement.icon != null) {
            quoteTypeImage.setImageResource(messageViewElement.icon)
            quoteTypeImage.visibility = View.VISIBLE
        }

        quotePopupListener?.onPostVisibilityChange()
    }

    fun adjustCornersToOpenState(layout: TextInputLayout, delayMs: Long) {
        layout.postDelayed(
            {
                layout.setBoxCornerRadiiResources(
                    R.dimen.compose_textinputlayout_radius_expanded,
                    R.dimen.compose_textinputlayout_radius_expanded,
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                )
            },
            delayMs,
        )
    }

    override fun dismiss() {
        anchorView?.let {
            it.postDelayed(
                {
                    it.setBoxCornerRadiiResources(
                        R.dimen.compose_textinputlayout_radius,
                        R.dimen.compose_textinputlayout_radius,
                        R.dimen.compose_textinputlayout_radius,
                        R.dimen.compose_textinputlayout_radius,
                    )
                },
                200,
            )
            it.removeOnLayoutChangeListener(onLayoutChangeListener)
            ViewCompat.setWindowInsetsAnimationCallback(it, null)
        }
        quotePopupListener?.onDismiss()
        super.dismiss()
        quotePopupListener?.onPostVisibilityChange()
    }

    interface QuotePopupListener {
        fun onHeightSet(height: Int)
        fun onDismiss()
        fun onPostVisibilityChange()
    }
}
