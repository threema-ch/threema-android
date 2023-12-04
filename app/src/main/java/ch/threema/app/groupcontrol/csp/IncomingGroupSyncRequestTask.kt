/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.groupcontrol.csp

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.MessageProcessor.ProcessingResult
import ch.threema.app.groupcontrol.IncomingGroupControlTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage
import ch.threema.storage.models.GroupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSyncRequestTask")

class IncomingGroupSyncRequestTask(
    private val groupSyncRequestMessage: GroupRequestSyncMessage,
    serviceManager: ServiceManager,
) : IncomingGroupControlTask {
    private val groupService = serviceManager.groupService
    private val groupCallManager = serviceManager.groupCallManager

    override fun run(): ProcessingResult {
        return executeTask()
    }

    override suspend fun invoke(scope: CoroutineScope): ProcessingResult {
        return withContext(scope.coroutineContext) { executeTask() }
    }

    private fun executeTask(): ProcessingResult {
        // 1. Look up the group. If the group could not be found, abort these steps
        val group = groupService.getByGroupMessage(groupSyncRequestMessage)
        if (group == null) {
            logger.warn("Discarding group sync request message because group could")
            return ProcessingResult.IGNORED
        }

        if (!groupService.isGroupCreator(group)) {
            logger.warn("Discarding group sync request message to non-owner")
            return ProcessingResult.IGNORED
        }

        val sender = groupSyncRequestMessage.fromIdentity

        // 2. If the group is marked as left or the sender is not a member of the group, send a
        // group-setup with an empty members list back to the sender and abort these steps.
        if (isLeftGroup(group) || !isSenderGroupMember(group)) {
            groupService.sendEmptyGroupSetup(group, sender)
            return ProcessingResult.IGNORED
        }

        // 3. Send a group setup message, 4. send a profile picture (if set), and 5. send a delete
        // profile picture (if not set)
        groupService.sendSync(group, arrayOf(sender))

        // 6. If a group call is currently considered running within this group, send a group call
        // start message
        groupCallManager.sendGroupCallStartToNewMembers(group, listOf(sender))

        return ProcessingResult.SUCCESS
    }

    private fun isLeftGroup(group: GroupModel): Boolean = !groupService.isGroupMember(group)

    private fun isSenderGroupMember(group: GroupModel): Boolean {
        return groupService.getMembers(group).map { it.identity }
            .contains(groupSyncRequestMessage.fromIdentity)
    }
}
