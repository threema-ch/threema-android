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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.util.Date

class OutgoingGroupEditMessageTask(
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val editedText: String,
    private val editedAt: Date,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingGroupEditMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = getGroupMessageModel(messageModelId)
                ?: throw ThreemaException("No group message model found for messageModelId=$messageModelId")

        val group = groupService.getById(message.groupId)
            ?: throw ThreemaException("No group model found for groupId=${message.groupId}")

        val editedMessageId = MessageId.fromString(message.apiMessageId).messageIdLong

        sendGroupMessage(
                group,
                groupService.getGroupIdentities(group).toSet(),
                null,
                messageId,
                createAbstractMessage =  { createEditMessage(editedMessageId, editedAt) },
                handle
        )
    }

    private fun createEditMessage(messageId: Long, date: Date): GroupEditMessage {
        val editMessage = GroupEditMessage(
            EditMessageData(
                messageId = messageId,
                text = editedText
            )
        )
        editMessage.date = date
        return editMessage
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupEditMessageData(
        messageModelId,
        messageId.messageId,
        editedText,
        editedAt.time,
        recipientIdentities
    )

    @Serializable
    class OutgoingGroupEditMessageData(
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val editedText: String,
        private val editedAt: Long,
        private val recipientIdentities: Set<String>
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupEditMessageTask(
                messageModelId,
                MessageId(messageId),
                editedText,
                Date(editedAt),
                recipientIdentities,
                serviceManager
            )
    }
}
