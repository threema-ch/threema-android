/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import android.content.res.ColorStateList
import androidx.appcompat.content.res.AppCompatResources
import ch.threema.app.R
import ch.threema.common.minus
import ch.threema.common.now
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.FirstUnreadMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

/**
 * Check whether the user should be able to edit the given message.
 *
 * @param belongsToNotesGroup Whether this message is part of a Notes Group, i.e., a local-only group with only the user in it
 * @param editTime The time at which the message would be edited, usually "now"
 * @param getMessageTime Used to determine the relevant date of the message, for determining its age
 * @return `true` if this message can still be edited, `false` otherwise
 */
@JvmOverloads
fun AbstractMessageModel.canBeEdited(
    belongsToNotesGroup: Boolean = false,
    editTime: Date = now(),
    getMessageTime: AbstractMessageModel.() -> Date? = AbstractMessageModel::createdAt,
): Boolean =
    type?.canBeEdited == true &&
        !isStatusMessage &&
        isOutbox &&
        (belongsToNotesGroup || getMessageTime()?.let { messageTime -> editTime - messageTime <= EditMessage.EDIT_MESSAGES_MAX_AGE } == true) &&
        (this is MessageModel || this is GroupMessageModel) &&
        (postedAt != null || state == MessageState.SENDFAILED) &&
        !isDeleted

/**
 * @return The correct color-state-list to use when rendering contents of this message model.
 */
fun AbstractMessageModel.getUiContentColor(context: Context): ColorStateList =
    if (this is FirstUnreadMessageModel) {
        ColorStateList.valueOf(
            ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSecondaryContainer),
        )
    } else if (isStatusMessage) {
        AppCompatResources.getColorStateList(context, R.color.bubble_text_status_colorstatelist)
    } else {
        AppCompatResources.getColorStateList(
            context,
            if (isOutbox) R.color.bubble_send_text_colorstatelist else R.color.bubble_receive_text_colorstatelist,
        )
    }

fun List<AbstractMessageModel>.findIndexByMessageId(messageId: Int): Int? =
    indexOfFirst { message -> message.id == messageId }
        .takeUnless { it == -1 }
