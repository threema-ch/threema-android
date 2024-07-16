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
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.util.Date

class OutgoingContactEditMessageTask(
    private val toIdentity: String,
    private val messageId: Int,
    private val text: String,
    private val editedAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingContactEditMessageTask"
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val message = messageService.getContactMessageModel(messageId, true)
                ?: throw ThreemaException("No contact message model found for messageId=$messageId")

        val editMessage = EditMessage(
            EditMessageData(
                messageId = MessageId.fromString(message.apiMessageId).messageIdLong,
                text = text
            )
        )
        editMessage.toIdentity = toIdentity
        editMessage.date = editedAt

        sendContactMessage(editMessage, null, handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingContactEditMessageData(
        toIdentity,
        messageId,
        text,
        editedAt.time
    )

    @Serializable
    class OutgoingContactEditMessageData(
        private val toIdentity: String,
        private val messageId: Int,
        private val text: String,
        private val editedAt: Long
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactEditMessageTask(
                toIdentity,
                messageId,
                text,
                Date(editedAt),
                serviceManager
            )
    }
}
