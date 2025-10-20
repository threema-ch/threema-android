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
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity
import java.util.Date

class OutgoingGroupCallStartTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    override val recipientIdentities: Set<Identity>,
    private val protocolVersion: UInt,
    private val gck: ByteArray,
    private val sfuBaseUrl: String,
    createdAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupCallStartTask"

    override val messageId = MessageId.random()

    override val date = createdAt

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        super.runSendingSteps(handle)

        groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)?.let {
            groupService.bumpLastUpdate(it)
        }
    }

    override fun createGroupMessage() =
        GroupCallStartMessage(GroupCallStartData(protocolVersion, gck, sfuBaseUrl))

    override fun serialize(): SerializableTaskData? = null
}
