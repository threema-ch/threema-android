/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupDeliverReceiptMessageTask")

class OutgoingGroupDeliveryReceiptMessageTask(
    private val messageModelId: Int,
    private val receiptType: Int,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingGroupDeliveryReceiptMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId)
        if (messageModel == null) {
            logger.warn("Message model ($messageModelId) is null while trying to send group delivery receipt")
            return
        }
        val group = groupService.getById(messageModel.groupId)
        if (group == null) {
            logger.warn("Group (${messageModel.groupId}) is null while trying to send group delivery receipt")
            return
        }

        val messageId = MessageId()

        sendGroupMessage(
            group,
            recipientIdentities,
            null,
            Date(),
            messageId,
            {
                GroupDeliveryReceiptMessage().also {
                    it.receiptType = receiptType
                    it.receiptMessageIds = arrayOf(MessageId.fromString(messageModel.apiMessageId))
                }
            },
            handle
        )
    }

    override fun serialize(): SerializableTaskData =
        OutgoingGroupDeliveryReceiptMessageData(messageModelId, receiptType, recipientIdentities)

    @Serializable
    class OutgoingGroupDeliveryReceiptMessageData(
        private val messageModelId: Int,
        private val receiptType: Int,
        private val recipientIdentities: Set<String>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupDeliveryReceiptMessageTask(
                messageModelId,
                receiptType,
                recipientIdentities,
                serviceManager
            )
    }
}
