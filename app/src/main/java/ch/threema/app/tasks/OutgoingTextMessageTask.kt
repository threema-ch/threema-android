/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT
import ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingTextMessageTask")

class OutgoingTextMessageTask(
    private val messageModelId: Int,
    @MessageReceiverType
    private val messageReceiverType: Int,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    private val messageService = serviceManager.messageService
    private val groupService = serviceManager.groupService

    override val type: String = "OutgoingTextMessageTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        when (messageReceiverType) {
            Type_CONTACT -> sendContactMessage(handle)
            Type_GROUP -> sendGroupMessage(handle)
            else -> throw IllegalStateException("Invalid message receiver type $messageReceiverType")
        }
    }

    private suspend fun sendContactMessage(handle: ActiveTaskCodec) {
        val messageModel = messageService.getContactMessageModel(messageModelId, true)
        if (messageModel == null) {
            logger.warn("Could not find message model with id {}", messageModelId)
            return
        }

        // Create the message
        val message = TextMessage()
        message.messageId = MessageId(Utils.hexStringToByteArray(messageModel.apiMessageId!!))
        message.text = messageModel.bodyAndQuotedMessageId
        message.toIdentity = messageModel.identity

        sendContactMessage(message, messageModel, handle)
    }

    private suspend fun sendGroupMessage(handle: ActiveTaskCodec) {
        val messageModel = messageService.getGroupMessageModel(messageModelId, true)
        if (messageModel == null) {
            logger.warn("Could not find message model with id {}", messageModelId)
            return
        }

        val group = groupService.getById(messageModel.groupId)
            ?: throw IllegalStateException("Could not get group for message model ${messageModel.apiMessageId}")

        val textIncludingQuote = messageModel.bodyAndQuotedMessageId

        sendGroupMessage(
            group,
            recipientIdentities,
            messageModel,
            MessageId(Utils.hexStringToByteArray(messageModel.apiMessageId!!)),
            {
                GroupTextMessage().apply {
                    text = textIncludingQuote
                }
            },
            handle
        )
    }

    override fun serialize(): SerializableTaskData =
        OutgoingTextMessageData(
            messageModelId,
            messageReceiverType,
            recipientIdentities,
        )

    @Serializable
    class OutgoingTextMessageData(
        private val messageModelId: Int,
        @MessageReceiverType
        private val receiverType: Int,
        private val recipientIdentities: Set<String>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingTextMessageTask(
                messageModelId,
                receiverType,
                recipientIdentities,
                serviceManager
            )
    }

}
