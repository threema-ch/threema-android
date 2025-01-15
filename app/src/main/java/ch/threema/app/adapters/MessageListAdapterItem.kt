/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.ContactService
import ch.threema.app.services.DeadlineListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.TestUtil
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageType

/**
 * This class is used to get the information of a conversation list item faster. These objects can
 * be created in advance and when the user scrolls through the list, the information can be
 * displayed quickly.
 */
class MessageListAdapterItem(
    val conversationModel: ConversationModel,
    contactService: ContactService,
    groupService: GroupService,
    private val mutedChatsListService: DeadlineListService,
    private val mentionOnlyChatsListService: DeadlineListService,
    private val ringtoneService: RingtoneService,
    private val hiddenChatsListService: DeadlineListService
) {
    val group: GroupModel? = conversationModel.group

    val isContactConversation = conversationModel.isContactConversation
    val isGroupConversation = conversationModel.isGroupConversation
    private val isDistributionListConversation = conversationModel.isDistributionListConversation
    val isNotesGroup = group?.let { groupService.isNotesGroup(it) } ?: false
    val isGroupMember = group?.let { groupService.isGroupMember(it) } ?: false

    private val uniqueId = conversationModel.receiver?.uniqueIdString ?: ""
    val uid: String = conversationModel.uid

    val isHidden: Boolean
        get() = hiddenChatsListService.has(uniqueId)
    val isPinTagged = conversationModel.isPinTagged
    val isTyping = conversationModel.isTyping

    // This string contains the drafted message (only the first 100 characters); no draft available if null
    private var lastDraft: CharSequence? = null
    private var lastDraftPadded: CharSequence? = getDraft()

    fun getDraft(): CharSequence? {
        val draft = ThreemaApplication.getMessageDraft(uniqueId)
        if (draft == lastDraft) {
            return lastDraftPadded
        }
        if (draft?.isNotBlank() == true) {
            lastDraft = draft
            lastDraftPadded = "$draft "
        } else {
            lastDraft = null
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

    val latestMessageGroupMemberName =
        if (isGroupConversation && latestMessage != null && latestMessage.type != MessageType.GROUP_CALL_STATUS && TestUtil.isBlankOrNull(getDraft())) {
            String.format(
                "%s: ",
                NameUtil.getShortName(conversationModel.context, latestMessage, contactService)
            )
        } else {
            ""
        }

    val deliveryIconResource = run {
        if (isContactConversation) {
            if (latestMessage != null) {
                if (latestMessage.type == MessageType.VOIP_STATUS) {
                    // Always show the phone icon for voip status messages
                    R.drawable.ic_phone_locked
                } else {
                    if (!latestMessage.isOutbox) {
                        R.drawable.ic_reply_filled
                    } else {
                        ConversationModel.NO_RESOURCE
                    }
                    // Note that the icon for outbox messages is handled directly in the view holder
                }
            } else {
                ConversationModel.NO_RESOURCE
            }
        } else if (isGroupConversation) {
            if (isNotesGroup) {
                R.drawable.ic_spiral_bound_booklet_outline
            } else {
                R.drawable.ic_group_filled
            }
        } else if (isDistributionListConversation) {
            R.drawable.ic_distribution_list_filled
        } else {
            ConversationModel.NO_RESOURCE
        }
    }

    val muteStatusResource = run {
        if (mutedChatsListService.has(uniqueId)) {
            R.drawable.ic_do_not_disturb_filled
        } else if (mentionOnlyChatsListService.has(uniqueId)) {
            R.drawable.ic_dnd_mention_black_18dp
        } else if (ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, isGroupConversation)) {
            R.drawable.ic_notifications_off_filled
        } else {
            ConversationModel.NO_RESOURCE
        }
    }
}

