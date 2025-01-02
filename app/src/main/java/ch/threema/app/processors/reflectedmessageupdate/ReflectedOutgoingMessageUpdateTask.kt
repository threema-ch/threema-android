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

package ch.threema.app.processors.reflectedmessageupdate

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.CONTACT
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.DISTRIBUTION_LIST
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.GROUP
import ch.threema.protobuf.d2d.MdD2D.ConversationId.IdCase.ID_NOT_SET
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessageUpdate.Update
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessageUpdate.Update.UpdateCase.SENT
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingMessageUpdateTask")

class ReflectedOutgoingMessageUpdateTask(
    private val outgoingMessageUpdate: MdD2D.OutgoingMessageUpdate,
    private val timestamp: ULong,
    serviceManager: ServiceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    fun run() {
        outgoingMessageUpdate.updatesList.forEach { update ->
            when (update.updateCase) {
                SENT -> applySentUpdate(update)
                else -> logger.error("Received an unknown outgoing message update '${update.updateCase}'")
            }
        }
    }

    private fun applySentUpdate(update: Update) {
        val conversation = update.conversation
        val messageId = MessageId(update.messageId)
        when (conversation.idCase) {
            CONTACT -> applyContactMessageSentUpdate(messageId, conversation.contact)

            GROUP -> applyGroupMessageSentUpdate(messageId, conversation.group)

            DISTRIBUTION_LIST -> updateDistributionListMessageState(
                messageId,
                conversation.distributionList
            )

            ID_NOT_SET -> logger.warn("Received outgoing message update where id is not set")

            null -> logger.warn("Received outgoing message update where id is null")
        }
    }

    private fun applyContactMessageSentUpdate(
        messageId: MessageId,
        recipientIdentity: String,
    ) {
        val messageModel = messageService.getContactMessageModel(messageId, recipientIdentity)

        if (messageModel == null) {
            logger.warn(
                "Message model for message {} to {} not found",
                messageId,
                recipientIdentity
            )
            return
        }

        updateMessageModelSentState(messageModel)
    }

    private fun applyGroupMessageSentUpdate(
        messageId: MessageId,
        groupIdentity: Common.GroupIdentity,
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

        updateMessageModelSentState(messageModel)
    }

    private fun updateDistributionListMessageState(
        messageId: MessageId,
        distributionList: Long,
    ) {
        // TODO(ANDR-2718)
    }

    private fun updateMessageModelSentState(messageModel: AbstractMessageModel) {
        messageService.updateOutgoingMessageState(
            messageModel,
            MessageState.SENT,
            Date(timestamp.toLong())
        )
        ListenerManager.messageListeners.handle {
            it.onModified(listOf(messageModel))
        }
    }
}
