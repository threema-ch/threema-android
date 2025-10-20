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

package ch.threema.app.compose.message

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.interop.InteropEmojiConversationTextView
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.app.messagedetails.MessageUiModel
import ch.threema.app.ui.CustomTextSelectionCallback
import ch.threema.app.utils.LocaleUtil
import ch.threema.common.now
import java.util.Date

@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    text: String,
    @StyleRes textAppearanceRes: Int = R.style.Threema_Bubble_Text_Body,
    messageBodyAlpha: Float = 1f,
    isOutbox: Boolean,
    shouldMarkupText: Boolean = true,
    textSelectionCallback: CustomTextSelectionCallback? = null,
    isTextSelectable: Boolean = false,
    onClick: (() -> Unit)? = null,
    @SuppressLint("ComposableLambdaParameterNaming")
    footerContent: @Composable ((contentColor: Color) -> Unit)? = null,
) {
    val bubbleColor: Color = if (isOutbox) {
        CustomColors.chatBubbleSendContainer
    } else {
        CustomColors.chatBubbleReceiveContainer
    }
    val contentColor: Color = if (isOutbox) {
        CustomColors.chatBubbleSendOnContainer
    } else {
        CustomColors.chatBubbleReceiveOnContainer
    }
    Column(
        modifier = modifier
            .background(
                color = bubbleColor,
                shape = RoundedCornerShape(16.dp),
            )
            .clip(RoundedCornerShape(16.dp))
            .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    ) {
        InteropEmojiConversationTextView(
            text = text,
            textAppearanceRes = textAppearanceRes,
            contentColor = contentColor.copy(
                alpha = messageBodyAlpha,
            ),
            shouldMarkupText = shouldMarkupText,
            textSelectionCallback = textSelectionCallback,
            isTextSelectable = isTextSelectable,
        )
        Spacer(modifier = Modifier.size(4.dp))
        footerContent?.invoke(contentColor)
    }
}

@Composable
fun CompleteMessageBubble(
    modifier: Modifier = Modifier,
    message: MessageUiModel,
    shouldMarkupText: Boolean,
    isTextSelectable: Boolean = false,
    textSelectionCallback: CustomTextSelectionCallback? = null,
) {
    val cdMessage = stringResource(R.string.cd_message)
    if (message.isDeleted) {
        DeletedMessageBubble(message.isOutbox, message.createdAt)
    } else {
        /**
         * If we encounter a blank/empty value in [MessageUiModel.text] we can
         * be sure it is an MessageUiModel of a file message. Because you cant
         * send empty text messages. So in this case we want to style the
         * message bubble body text slightly different and set a placeholder
         * text like `No caption`
         */
        val isEmptyFileMessageCaption: Boolean = message.text.isBlank()

        MessageBubble(
            modifier = modifier.semantics(mergeDescendants = true) {
                contentDescription = cdMessage
                isTraversalGroup = true
            },
            text = message.text.takeIf(String::isNotBlank)
                ?: stringResource(R.string.edit_history_file_no_caption),
            isOutbox = message.isOutbox,
            shouldMarkupText = shouldMarkupText,
            textSelectionCallback = textSelectionCallback,
            isTextSelectable = isTextSelectable,
            textAppearanceRes = if (isEmptyFileMessageCaption) {
                R.style.Threema_Bubble_Text_Body_HistoryNoCaption
            } else {
                R.style.Threema_Bubble_Text_Body
            },
            messageBodyAlpha = if (isEmptyFileMessageCaption) {
                0.6f
            } else {
                1f
            },
            footerContent = { contentColor: Color ->
                MessageBubbleFooter(
                    shouldShowEditedLabel = message.editedAt != null,
                    date = message.createdAt,
                    isOutbox = message.isOutbox,
                    deliveryIconRes = message.deliveryIconRes,
                    deliveryIconContentDescriptionRes = message.deliveryIconContentDescriptionRes,
                    contentColor = contentColor,
                )
            },
        )
    }
}

@Composable
fun DeletedMessageBubble(
    isOutbox: Boolean,
    date: Date,
    onClick: (() -> Unit)? = null,
) {
    MessageBubble(
        text = stringResource(R.string.message_was_deleted),
        textAppearanceRes = R.style.Threema_Bubble_Text_Body_Deleted,
        messageBodyAlpha = 0.6f,
        isOutbox = isOutbox,
        onClick = onClick,
        footerContent = { contentColor ->
            MessageBubbleFooter(
                shouldShowEditedLabel = false,
                isOutbox = isOutbox,
                date = date,
                contentColor = contentColor,
            )
        },
    )
}

const val CONTENT_ALPHA_BOTTOM_ROW = 0.6f

@Composable
fun MessageBubbleFooter(
    shouldShowEditedLabel: Boolean,
    date: Date? = null,
    isOutbox: Boolean,
    @DrawableRes deliveryIconRes: Int? = null,
    @StringRes deliveryIconContentDescriptionRes: Int? = null,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shouldShowEditedLabel) {
            ThemedText(
                modifier = stringResource(R.string.cd_edited).let {
                    Modifier.semantics {
                        contentDescription = it
                    }
                },
                text = stringResource(R.string.edited),
                style = AppTypography.bodySmall,
                color = contentColor.copy(
                    alpha = CONTENT_ALPHA_BOTTOM_ROW,
                ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        date?.let {
            Spacer(modifier = Modifier.size(4.dp))
            val formattedDate = LocaleUtil.formatTimeStampString(LocalContext.current, it.time, true)
            ThemedText(
                modifier = stringResource(R.string.cd_created_at).let {
                    Modifier.semantics {
                        contentDescription = it.format(formattedDate)
                    }
                },
                text = formattedDate,
                style = AppTypography.bodySmall,
                color = contentColor.copy(
                    alpha = CONTENT_ALPHA_BOTTOM_ROW,
                ),
            )
        }
        if (deliveryIconRes != null && isOutbox) {
            Spacer(modifier = Modifier.size(8.dp))
            MessageStateIndicator(
                deliveryIconRes = deliveryIconRes,
                deliveryIconContentDescriptionRes = deliveryIconContentDescriptionRes,
                deliveryIndicatorTintColor = contentColor.copy(
                    alpha = CONTENT_ALPHA_BOTTOM_ROW,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun MessageBubble_Preview() {
    MessageBubble(
        text = "Lorem ipsum *dolor sit amet*, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam",
        isOutbox = true,
        shouldMarkupText = true,
        footerContent = { contentColor: Color ->
            MessageBubbleFooter(
                shouldShowEditedLabel = true,
                date = now(),
                isOutbox = true,
                deliveryIconRes = R.drawable.ic_mark_read,
                contentColor = contentColor,
            )
        },
    )
}
