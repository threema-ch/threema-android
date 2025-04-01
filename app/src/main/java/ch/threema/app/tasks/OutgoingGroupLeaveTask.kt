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
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

class OutgoingGroupLeaveTask(
    override val groupId: GroupId,
    override val creatorIdentity: String,
    override val recipientIdentities: Set<String>,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupLeaveTask"

    override val messageId = messageId ?: MessageId()

    override fun createGroupMessage() = GroupLeaveMessage()

    override fun serialize(): SerializableTaskData = OutgoingGroupLeaveData(
        groupId.groupId, creatorIdentity, recipientIdentities, messageId.messageId
    )

    @Serializable
    class OutgoingGroupLeaveData(
        private val groupId: ByteArray,
        private val creatorIdentity: String,
        private val receiverIdentities: Set<String>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupLeaveTask(
                GroupId(groupId),
                creatorIdentity,
                receiverIdentities,
                MessageId(messageId),
                serviceManager,
            )
    }

}
