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

package ch.threema.app.groupcontrol.csp

import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.MessageProcessor.ProcessingResult
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.DISCARD_MESSAGE
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT
import ch.threema.app.groupcontrol.IncomingGroupControlTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupNameTask")

/**
 * The task to run the required steps when a group rename message has been received.
 */
class IncomingGroupNameTask(
    private val groupRenameMessage: GroupRenameMessage,
    serviceManager: ServiceManager,
) : IncomingGroupControlTask {
    private val groupService = serviceManager.groupService
    private val databaseService = serviceManager.databaseServiceNew

    override fun run(): ProcessingResult {
        return executeTask()
    }

    override suspend fun invoke(scope: CoroutineScope): ProcessingResult {
        return withContext(scope.coroutineContext) { executeTask() }
    }

    private fun executeTask(): ProcessingResult {
        // 1. Run the common group receive steps
        val stepsResult = groupService.runCommonGroupReceiveSteps(groupRenameMessage)
        if (stepsResult == DISCARD_MESSAGE || stepsResult == SYNC_REQUEST_SENT) {
            return ProcessingResult.IGNORED
        }

        val groupModel = groupService.getByGroupMessage(groupRenameMessage)
        if (groupModel == null) {
            logger.warn("Discarding group name message because group could not be found")
            return ProcessingResult.IGNORED
        }

        val newGroupName = groupRenameMessage.groupName ?: ""
        val oldGroupName = groupModel.name ?: ""

        // 2. Update the group with the given name (only if the new name is different)
        if (oldGroupName != newGroupName) {
            groupModel.name = newGroupName
            val success = databaseService.groupModelFactory.createOrUpdate(groupModel)
            if (!success) {
                logger.error("Failed to update the group model")
                return ProcessingResult.IGNORED
            }

            ListenerManager.groupListeners.handle { listener: GroupListener ->
                listener.onRename(groupModel)
            }
        }

        return ProcessingResult.SUCCESS
    }
}
