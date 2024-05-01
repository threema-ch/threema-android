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

package ch.threema.app.processors.groupcontrol

import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupNameTask")

/**
 * The task to run the required steps when a group rename message has been received.
 */
class IncomingGroupNameTask(
    private val groupNameMessage: GroupNameMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val databaseService = serviceManager.databaseServiceNew

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Run the common group receive steps
        val groupModel = runCommonGroupReceiveSteps(groupNameMessage, handle, serviceManager)
        if (groupModel == null) {
            logger.warn("Discarding group name message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        val newGroupName = groupNameMessage.groupName ?: ""
        val oldGroupName = groupModel.name ?: ""

        // 2. Update the group with the given name (only if the new name is different)
        if (oldGroupName != newGroupName) {
            groupModel.name = newGroupName
            val success = databaseService.groupModelFactory.createOrUpdate(groupModel)
            if (!success) {
                logger.error("Failed to update the group model")
                return ReceiveStepsResult.DISCARD
            }

            ListenerManager.groupListeners.handle { listener: GroupListener ->
                listener.onRename(groupModel)
            }
        }

        return ReceiveStepsResult.SUCCESS
    }
}
