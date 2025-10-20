/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.storage.models

import android.content.Context
import androidx.annotation.DrawableRes
import ch.threema.app.R
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.utils.ConversationUtil.getDistributionListConversationUid
import ch.threema.app.utils.ConversationUtil.getGroupConversationUid
import ch.threema.app.utils.ConversationUtil.getIdentityConversationUid
import java.util.Date

class ConversationModel(
    val context: Context,
    @JvmField var messageReceiver: MessageReceiver<*>,
) {
    val isContactConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_CONTACT

    val isGroupConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_GROUP

    val isDistributionListConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_DISTRIBUTION_LIST

    val contact: ContactModel?
        get() = when {
            isContactConversation -> (messageReceiver as ContactMessageReceiver).contact
            else -> null
        }

    val group: GroupModel?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).group
            else -> null
        }

    val groupModel: ch.threema.data.models.GroupModel?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).groupModel
            else -> null
        }

    val distributionList: DistributionListModel?
        get() = when {
            isDistributionListConversation -> (messageReceiver as DistributionListMessageReceiver).distributionList
            else -> null
        }

    @JvmField
    var messageCount: Long = 0L

    @JvmField
    var latestMessage: AbstractMessageModel? = null

    var unreadCount: Long = 0L
        set(value) {
            field = value
            if (value == 0L) {
                isUnreadTagged = false
            }
        }

    @JvmField
    var isUnreadTagged: Boolean = false

    @JvmField
    var isArchived: Boolean = false

    val uid: String
        get() = when {
            isContactConversation -> getIdentityConversationUid(contact!!.identity)
            isGroupConversation -> getGroupConversationUid(group!!.id.toLong())
            isDistributionListConversation -> getDistributionListConversationUid(distributionList!!.id)
            else -> throw IllegalStateException("Can not determine uid of conversation model for receiver od type ${messageReceiver.type}")
        }

    var position: Int = -1

    @JvmField
    var lastUpdate: Date? = null

    @JvmField
    var isTyping: Boolean = false

    @JvmField
    var isPinTagged: Boolean = false

    /**
     * @return Return the date used for sorting. Corresponds to [lastUpdate] if set.
     */
    val sortDate: Date
        get() = when {
            lastUpdate != null -> lastUpdate!!
            else -> Date(0)
        }

    fun hasUnreadMessage(): Boolean = unreadCount > 0

    @DrawableRes
    fun getConversationIconRes(): Int? = when {
        isContactConversation -> latestMessage?.let { messageModel ->
            when {
                messageModel.type == MessageType.VOIP_STATUS -> R.drawable.ic_phone_locked
                !messageModel.isOutbox -> R.drawable.ic_reply_filled
                else -> null
            }
        }

        isGroupConversation -> when {
            groupModel?.isNotesGroup() == true -> R.drawable.ic_spiral_bound_booklet_outline
            else -> R.drawable.ic_group_filled
        }

        isDistributionListConversation -> R.drawable.ic_distribution_list_filled

        else -> null
    }

    val receiverModel: ReceiverModel
        get() = contact ?: group ?: distributionList ?: throw IllegalStateException("ConversationModel is missing a ReceiverModel")

    override fun toString(): String = messageReceiver.displayName
}
