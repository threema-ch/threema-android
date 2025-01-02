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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.OutgoingGroupLeaveTask
import ch.threema.app.tasks.OutgoingGroupSetupTask
import ch.threema.app.tasks.OutgoingGroupSyncRequestTask
import ch.threema.app.utils.TestUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.GroupModel

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupUtils")

suspend fun runCommonGroupReceiveSteps(
    message: AbstractGroupMessage,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? = runCommonGroupReceiveSteps(
    message.apiGroupId,
    message.groupCreator,
    message.fromIdentity,
    handle,
    serviceManager
)

/**
 * Run the common group receive steps.
 *
 * @return the group model if the steps completed successfully, null otherwise
 */
suspend fun runCommonGroupReceiveSteps(
    groupId: GroupId,
    creatorIdentity: String,
    fromIdentity: String,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? {
    val userService = serviceManager.userService
    val groupService = serviceManager.groupService

    // 1. Look up the group
    val groupModel = groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)

    // 2. Check if the group could be found
    if (groupModel == null) {
        if (TestUtil.compare(creatorIdentity, userService.identity)) {
            // 2.1 If the user is the creator of the group (as alleged by received message),
            // discard the received message and abort these steps
            logger.info("Could not find group with me as creator")
            return null
        }
        // 2.2 Send a group-sync-request to the group creator
        sendGroupSyncRequest(groupId, creatorIdentity, serviceManager, handle)
        return null
    }

    // 3. Check if the group is left
    if (!groupService.isGroupMember(groupModel)) {
        if (groupService.isGroupCreator(groupModel)) {
            // 3.1 If the user is the creator, send a group-setup with an empty
            // members list back to the sender and discard the received message.
            logger.info("Got a message in a left group where I am the creator")
            sendEmptyGroupSetup(groupModel, fromIdentity, handle, serviceManager)
        } else {
            // 3.2 Send a group leave to the sender and discard the received message
            logger.info("Got a message in a left group")
            OutgoingGroupLeaveTask(
                groupId,
                creatorIdentity,
                setOf(fromIdentity),
                null,
                serviceManager
            ).invoke(handle)
        }
        return null
    }

    // 4. If the sender is not a member of the group and the user is the creator of the group,
    // send a group-setup with an empty members list back to the sender and discard the received
    // message.
    if (!groupService.getGroupIdentities(groupModel).contains(fromIdentity)) {
        logger.info("Got a message in a group from a sender that is not a member")
        if (groupService.isGroupCreator(groupModel)) {
            // 4.1 If the user is the creator of the group, send a group-setup with an empty member
            // list back
            sendEmptyGroupSetup(groupModel, fromIdentity, handle, serviceManager)
        } else {
            // 4.2 Send a group-sync-request to the group creator
            sendGroupSyncRequest(groupId, creatorIdentity, serviceManager, handle)
        }
        // Abort these steps
        return null
    }
    return groupModel
}

suspend fun sendEmptyGroupSetup(
    groupModel: GroupModel,
    receiverIdentity: String,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
) {
    OutgoingGroupSetupTask(
        groupModel.apiGroupId,
        groupModel.creatorIdentity,
        emptySet(),
        setOf(receiverIdentity),
        null,
        serviceManager
    ).invoke(handle)
}

private suspend fun sendGroupSyncRequest(
    groupId: GroupId,
    creatorIdentity: String,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    OutgoingGroupSyncRequestTask(
        groupId,
        creatorIdentity,
        null,
        serviceManager
    ).invoke(handle)
}
