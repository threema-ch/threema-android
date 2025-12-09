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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingGroupNameTask")

/**
 * The task to run the required steps when a group rename message has been received.
 */
class IncomingGroupNameTask(
    message: GroupNameMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupNameMessage>(message, triggerSource, serviceManager) {
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // Run the common group receive steps
        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
        val data = groupModel?.data
        if (groupModel == null || data == null) {
            logger.warn("Discarding group name message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        val newGroupName = message.groupName ?: ""

        if (data.name == newGroupName) {
            logger.info("Name has not changed")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult = ReflectGroupSyncUpdateImmediateTask.ReflectGroupName(
                newGroupName,
                groupModel,
                nonceFactory,
                multiDeviceManager,
            ).reflect(handle)

            when (reflectionResult) {
                is ReflectionResult.PreconditionFailed -> {
                    logger.warn(
                        "Group sync race occurred: Could not reflect group name",
                        reflectionResult.transactionException,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.Failed -> {
                    logger.error("Could not reflect group name", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.Success -> Unit
            }
        }

        groupModel.persistName(newGroupName)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group name from sync")
        return ReceiveStepsResult.DISCARD
    }
}
