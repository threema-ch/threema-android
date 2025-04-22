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
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

class OutgoingGroupNameTask(
    override val groupId: GroupId,
    override val creatorIdentity: String,
    private val groupName: String,
    override val recipientIdentities: Set<String>,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupNameTask"

    override val messageId = messageId ?: MessageId()

    override fun createGroupMessage() = GroupNameMessage().also { it.groupName = groupName }

    override fun serialize(): SerializableTaskData = OutgoingGroupNameData(
        groupId.groupId,
        creatorIdentity,
        groupName,
        recipientIdentities,
        messageId.messageId,
    )

    @Serializable
    class OutgoingGroupNameData(
        private val groupId: ByteArray,
        private val creatorIdentity: String,
        private val groupName: String,
        private val receiverIdentities: Set<String>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupNameTask(
                GroupId(groupId),
                creatorIdentity,
                groupName,
                receiverIdentities,
                MessageId(messageId),
                serviceManager,
            )
    }
}
