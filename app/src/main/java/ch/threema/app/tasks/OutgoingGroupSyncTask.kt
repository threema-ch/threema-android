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
import ch.threema.domain.models.GroupId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupSyncTask")

/**
 * Send a group sync to the given receiver identities. This includes sending a setup message, a
 * name message, and a set-profile-picture or delete-profile-picture message. Note that this task
 * does not update the synchronized-at-timestamp of the group as this task may also be used to send
 * a sync to individual members.
 */
class OutgoingGroupSyncTask(
    private val groupId: GroupId,
    private val creatorIdentity: String,
    private val receiverIdentities: Set<String>,
    private val serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupSyncTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val myIdentity = userService.identity
        if (creatorIdentity != myIdentity) {
            logger.warn("Only the group creator should send a group sync")
            return
        }

        val group = groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)
        if (group == null) {
            logger.error(
                "Could not find group {} with creator {} to send a group sync",
                groupId,
                creatorIdentity,
            )
            return
        }

        // Send a group setup message (run task immediately)
        OutgoingGroupSetupTask(
            groupId,
            creatorIdentity,
            groupService.getGroupMemberIdentities(group).toSet(),
            receiverIdentities,
            null,
            serviceManager,
        ).invoke(handle)

        // Send a group name message (run task immediately)
        OutgoingGroupNameTask(
            groupId,
            creatorIdentity,
            group.name ?: "",
            receiverIdentities,
            null,
            serviceManager,
        ).invoke(handle)

        // Send a profile picture (delete) message (run task immediately)
        OutgoingGroupProfilePictureTask(
            groupId,
            creatorIdentity,
            receiverIdentities,
            null,
            serviceManager,
        ).invoke(handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupSyncData(
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
        receiverIdentities = receiverIdentities,
    )

    @Serializable
    private class OutgoingGroupSyncData(
        private val groupId: ByteArray,
        private val creatorIdentity: String,
        private val receiverIdentities: Set<String>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupSyncTask(
                GroupId(groupId),
                creatorIdentity,
                receiverIdentities,
                serviceManager,
            )
    }
}
