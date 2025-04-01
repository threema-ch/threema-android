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

package ch.threema.app.processors.reflectedmessageupdate

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.ConversationNotificationUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.protobuf.Common.GroupIdentity
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.CONTACT
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.DISTRIBUTION_LIST
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.GROUP
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.ID_NOT_SET
import ch.threema.protobuf.d2d.MdD2D.IncomingMessageUpdate
import ch.threema.protobuf.d2d.MdD2D.IncomingMessageUpdate.Update.UpdateCase.READ
import ch.threema.storage.models.AbstractMessageModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedIncomingMessageUpdateTask")

class ReflectedIncomingMessageUpdateTask(
    private val incomingMessageUpdate: IncomingMessageUpdate,
    serviceManager: ServiceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val notificationService by lazy { serviceManager.notificationService }

    fun run() {
        logger.info("Processing reflected incoming message update")

        incomingMessageUpdate.updatesList.forEach { update ->
            when (update.updateCase) {
                READ -> applyReadUpdate(update)
                else -> logger.error("Received an unknown incoming message update '${update.updateCase}'")
            }
        }
    }

    private fun applyReadUpdate(update: IncomingMessageUpdate.Update) {
        val conversation = update.conversation
        val messageId = MessageId(update.messageId)
        val readAt = update.read.at
        when (conversation.idCase) {
            CONTACT -> applyContactMessageReadUpdate(messageId, conversation.contact, readAt)

            GROUP -> applyGroupMessageReadUpdate(messageId, conversation.group, readAt)

            DISTRIBUTION_LIST -> throw IllegalStateException(
                "Received incoming message update for a distribution list"
            )

            ID_NOT_SET -> logger.warn("Received incoming message update where id is not set")

            null -> logger.warn("Received incoming message update where id is null")
        }
    }

    private fun applyContactMessageReadUpdate(
        messageId: MessageId,
        senderIdentity: String,
        readAt: Long,
    ) {
        val messageModel = messageService.getContactMessageModel(messageId, senderIdentity)
        if (messageModel == null) {
            logger.warn("Message model for message {} of {} not found", messageId, senderIdentity)
            return
        }

        markMessageModelAsRead(messageModel, readAt)
    }

    private fun applyGroupMessageReadUpdate(
        messageId: MessageId,
        groupIdentity: GroupIdentity,
        readAt: Long,
    ) {
        val messageModel = messageService.getGroupMessageModel(
            messageId,
            groupIdentity.creatorIdentity,
            GroupId(groupIdentity.groupId)
        )

        if (messageModel == null) {
            logger.warn("Group message model for message {} not found", messageId)
            return
        }

        markMessageModelAsRead(messageModel, readAt)
    }

    private fun markMessageModelAsRead(messageModel: AbstractMessageModel, readAt: Long) {
        messageModel.setRead(true)
        Date(readAt).let {
            messageModel.setReadAt(it)
            messageModel.setModifiedAt(it)
        }
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
        cancelNotification(messageModel)
    }

    private fun cancelNotification(messageModel: AbstractMessageModel) {
        // Get notification UIDs of the messages that have just been marked as read
        val notificationUid = ConversationNotificationUtil.getUid(messageModel)

        // Cancel notification
        notificationService.cancelConversationNotification(notificationUid)
    }
}
