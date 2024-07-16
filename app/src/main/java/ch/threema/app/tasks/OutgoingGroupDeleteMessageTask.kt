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
import ch.threema.domain.protocol.csp.messages.DeleteMessageData
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.util.Date

class OutgoingGroupDeleteMessageTask(
    private val messageId: Int,
    private val deletedAt: Date,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingGroupDeleteMessageTask"

    private val groupService by lazy { serviceManager.groupService }
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val message = messageService.getGroupMessageModel(messageId, true)
            ?: throw ThreemaException("No group message model found for messageId=$messageId")

        val apiMessageId = MessageId.fromString(message.apiMessageId)

        val group = groupService.getById(message.groupId)
            ?: throw ThreemaException("No group model found for groupId=${message.groupId}")

        sendGroupMessage(
            group,
            recipientIdentities,
            null,
            apiMessageId,
            createAbstractMessage =  { createDeleteMessage(apiMessageId.messageIdLong, deletedAt) },
            handle
        )
    }

    private fun createDeleteMessage(messageId: Long, date: Date) : GroupDeleteMessage {
        val deleteMessage = GroupDeleteMessage(
            DeleteMessageData(messageId = messageId)
        )
        deleteMessage.date = date
        return deleteMessage
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupDeleteMessageData(messageId, deletedAt.time, recipientIdentities)

    @Serializable
    class OutgoingGroupDeleteMessageData(
        private val messageId: Int,
        private val deletedAt: Long,
        private val recipientIdentities: Set<String>
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupDeleteMessageTask(
                messageId,
                Date(deletedAt),
                recipientIdentities,
                serviceManager
            )
    }
}
