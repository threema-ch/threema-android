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

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.services.GroupService
import ch.threema.app.tasks.OutgoingGroupSyncRequestTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupLeaveTask")

class IncomingGroupLeaveTask(
    message: GroupLeaveMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupLeaveMessage>(message, triggerSource, serviceManager) {
    private val groupService = serviceManager.groupService
    private val userService = serviceManager.userService
    private val groupCallManager = serviceManager.groupCallManager

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val creator = message.groupCreator
        val sender = message.fromIdentity

        // 1. If the sender is the creator of the group, abort these steps
        if (sender == creator) {
            logger.warn("Discarding group leave message from group creator")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Look up the group
        val group = groupService.getByGroupMessage(message)

        // 3. If the group could not be found or is marked as left
        if (group == null || !groupService.isGroupMember(group)) {
            // 3.1 If the user is the creator of the group, abort these steps
            if (userService.identity == creator) {
                return ReceiveStepsResult.DISCARD
            }
            // 3.2 Send a group-sync-request to the group creator and abort these steps
            OutgoingGroupSyncRequestTask(
                message.apiGroupId,
                creator,
                null,
                serviceManager
            ).invoke(handle)
            return ReceiveStepsResult.DISCARD
        }

        @GroupService.GroupState val oldGroupState = groupService.getGroupState(group)

        // 4. Remove the member from the local group
        if (groupService.removeMemberFromGroup(group, sender)) {
            ListenerManager.groupListeners.handle { it.onMemberLeave(group, sender) }
        }
        // Reset the cache
        groupService.resetCache(group.id)

        @GroupService.GroupState val newGroupState = groupService.getGroupState(group)

        // Trigger a state change if the group transitions from a people group to a notes group
        if (oldGroupState != newGroupState) {
            ListenerManager.groupListeners.handle {
                it.onGroupStateChanged(group, oldGroupState, newGroupState)
            }
        }

        // 5. Run the rejected messages refresh steps for the group
        groupService.runRejectedMessagesRefreshSteps(group)

        // 6. If the user and the sender are participating in a group call of this group, remove the
        // sender from the group call (handle it as if the sender left the call)
        groupCallManager.updateAllowedCallParticipants(group)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}
