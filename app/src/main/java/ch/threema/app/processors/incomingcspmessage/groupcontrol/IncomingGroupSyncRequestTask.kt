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

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.OutgoingGroupSyncTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupModel

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSyncRequestTask")

class IncomingGroupSyncRequestTask(
    message: GroupSyncRequestMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSyncRequestMessage>(message, triggerSource, serviceManager) {
    private val groupService by lazy { serviceManager.groupService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Look up the group. If the group could not be found, abort these steps
        val group = groupService.getByGroupMessage(message)
        if (group == null) {
            logger.warn("Discarding group sync request message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        if (!groupService.isGroupCreator(group)) {
            logger.warn("Discarding group sync request message to non-creator")
            return ReceiveStepsResult.DISCARD
        }

        return handleIncomingGroupSyncRequest(
            group,
            message.fromIdentity,
            handle,
            serviceManager
        )
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}

suspend fun handleIncomingGroupSyncRequest(
    group: GroupModel,
    sender: String,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): ReceiveStepsResult {
    val groupService = serviceManager.groupService
    val incomingGroupSyncRequestLogModelFactory =
        serviceManager.databaseServiceNew.incomingGroupSyncRequestLogModelFactory

    // 2. If a group-sync-request from this sender and group has already been handled within the
    //    last hour, log a notice and abort these steps
    val groupSyncLog = incomingGroupSyncRequestLogModelFactory.getByGroupIdAndSenderIdentity(
        group.id, sender
    )
    val now = System.currentTimeMillis()
    val oneHourAgo = now - DateUtils.HOUR_IN_MILLIS
    if (groupSyncLog.lastHandledRequest > oneHourAgo) {
        logger.info("Group sync request already handled {}ms ago", now - groupSyncLog.lastHandledRequest)
        return ReceiveStepsResult.DISCARD
    }
    incomingGroupSyncRequestLogModelFactory.createOrUpdate(
        groupSyncLog.apply { lastHandledRequest = now }
    )

    // 3. If the group is marked as left or the sender is not a member of the group, send a
    //    group-setup with an empty members list back to the sender and abort these steps.
    if (!groupService.isGroupMember(group) || !groupService.isGroupMember(group, sender)) {
        sendEmptyGroupSetup(group, sender, handle, serviceManager)
        return ReceiveStepsResult.DISCARD
    }

    // 4. Send a group-setup message followed by a group-name message, 5. send a
    //    set-profile-picture (if set), and 6. send a delete-profile-picture (if not set)
    OutgoingGroupSyncTask(
        group.apiGroupId,
        group.creatorIdentity,
        setOf(sender),
        serviceManager
    ).invoke(handle)

    // 7. If a group call is currently considered running within this group, send a group call
    //    start message
    serviceManager.groupCallManager.sendGroupCallStartToNewMembers(group, setOf(sender), handle)

    return ReceiveStepsResult.SUCCESS
}
