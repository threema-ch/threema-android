/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.adapters

import androidx.annotation.DrawableRes
import ch.threema.app.R
import ch.threema.app.drafts.DraftManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.data.models.GroupModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageType

/**
 * This class is used to get the information of a conversation list item faster. These objects can
 * be created in advance and when the user scrolls through the list, the information can be
 * displayed quickly.
 */
class MessageListAdapterItem(
    val conversationModel: ConversationModel,
    private val contactService: ContactService,
    private val ringtoneService: RingtoneService,
    private val conversationCategoryService: ConversationCategoryService,
    private val draftManager: DraftManager,
) {
    val groupModel: GroupModel? = conversationModel.groupModel

    val isContactConversation = conversationModel.isContactConversation
    val isGroupConversation = conversationModel.isGroupConversation
    fun isNotesGroup() = groupModel?.isNotesGroup() ?: false
    fun isGroupMember() = groupModel?.data?.isMember ?: false

    private val uniqueId = conversationModel.messageReceiver.uniqueIdString
    val uid: String = conversationModel.uid

    val isPrivateChat: Boolean
        get() = conversationCategoryService.isPrivateChat(uniqueId)
    val isPinTagged = conversationModel.isPinTagged
    val isTyping = conversationModel.isTyping

    private var lastDraftMessage: String? = null
    private var lastDraftPadded: CharSequence? = getDraft()

    fun getDraft(): CharSequence? {
        val draft = draftManager.get(uniqueId)
        val draftMessage = draft?.text
        if (draft == lastDraftPadded) {
            return lastDraftPadded
        }
        if (draftMessage != null) {
            lastDraftMessage = draftMessage
            lastDraftPadded = "$draftMessage "
        } else {
            lastDraftMessage = null
            lastDraftPadded = null
        }
        return lastDraftPadded
    }

    // This string contains the number of unread messages. If empty, the conversation is tagged unread
    val unreadCountText = if (conversationModel.unreadCount > 0) {
        conversationModel.unreadCount.toString()
    } else if (conversationModel.isUnreadTagged) {
        ""
    } else {
        null
    }

    val latestMessage = conversationModel.latestMessage
    val latestMessageDate: String?
        get() = MessageUtil.getDisplayDate(conversationModel.context, latestMessage, false)
    val latestMessageDateContentDescription
        get() = "." + conversationModel.context.getString(R.string.state_dialog_modified) + "." + latestMessageDate + "."
    val latestMessageViewElement =
        latestMessage?.let { MessageUtil.getViewElement(conversationModel.context, it) }

    val latestMessageSubject: String by lazy {
        var subject: String? = ""
        if (latestMessageViewElement != null) {
            subject = latestMessageViewElement.text
        }
        if (latestMessage != null && latestMessage.type == MessageType.TEXT) {
            // we need to add an arbitrary character - otherwise span-only strings are formatted incorrectly in the item layout
            subject += " "
        }
        if (subject.isNullOrBlank()) {
            ""
        } else {
            // Append space if attachmentView is visible
            if (latestMessageViewElement?.icon != null) {
                subject = " $subject"
            }
            subject
        }
    }

    val latestMessageGroupMemberName
        get() =
            if (
                isGroupConversation &&
                latestMessage != null &&
                latestMessage.type != MessageType.GROUP_CALL_STATUS &&
                getDraft().isNullOrBlank()
            ) {
                String.format(
                    "%s: ",
                    NameUtil.getShortName(conversationModel.context, latestMessage, contactService),
                )
            } else {
                ""
            }

    val muteStatusDrawableRes: Int?
        @DrawableRes
        get() {
            var iconRes: Int? = null
            val messageReceiver = conversationModel.messageReceiver
            if (messageReceiver is ContactMessageReceiver) {
                iconRes = messageReceiver.contact.currentNotificationTriggerPolicyOverride().iconResRightNow
            } else if (messageReceiver is GroupMessageReceiver) {
                iconRes = messageReceiver.group.currentNotificationTriggerPolicyOverride().iconResRightNow
            }
            if (iconRes == null && ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, isGroupConversation)) {
                iconRes = R.drawable.ic_notifications_off_filled
            }
            return iconRes
        }
}
