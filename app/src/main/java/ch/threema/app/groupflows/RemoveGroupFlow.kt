/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.groupflows

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.GroupService
import ch.threema.app.tasks.ReflectGroupSyncDeletePrecondition
import ch.threema.app.tasks.ReflectGroupSyncDeleteTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TriggerSource
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("RemoveGroupFlow")

class RemoveGroupFlow(
    private val groupModel: GroupModel,
    private val groupService: GroupService,
    private val groupModelRepository: GroupModelRepository,
    private val multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
    private val taskManager: TaskManager,
    private val connection: ServerConnection,
) : BackgroundTask<GroupFlowResult> {

    override fun runInBackground(): GroupFlowResult {
        logger.info("Running remove group flow")

        if (groupModel.data.value?.isMember == true) {
            logger.error("Cannot remove group where the user is still a member")
            return GroupFlowResult.Failure.Other
        }

        // First, reflect the deletion (if md is active)
        if (multiDeviceManager.isMultiDeviceActive) {
            if (connection.connectionState != ConnectionState.LOGGEDIN) {
                return GroupFlowResult.Failure.Network
            }
            when (val reflectionResult = reflect()) {
                is ReflectionResult.Success -> {
                    logger.info("Reflected group delete successfully")
                }

                is ReflectionResult.Failed -> {
                    logger.error("Reflection failed", reflectionResult.exception)
                    return GroupFlowResult.Failure.Other
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is a very rare edge case that should not be possible at all. If it happens, it is fine to continue here because
                    // it is fine to just skip the reflection when multi device is not active anymore.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.error(
                        "Reflection failed due to precondition",
                        reflectionResult.transactionException,
                    )
                    return GroupFlowResult.Failure.Other
                }
            }
        }

        // Then persist the changes locally
        persist()

        // As the group has already been left or disbanded, there is no need to send any csp
        // messages

        return GroupFlowResult.Success(groupModel)
    }

    private fun reflect(): ReflectionResult<Unit> = runBlocking {
        taskManager.schedule(
            ReflectGroupSyncDeleteTask(
                groupModel,
                ReflectGroupSyncDeletePrecondition.USER_IS_NO_MEMBER,
                nonceFactory,
                multiDeviceManager,
            ),
        ).await()
    }

    private fun persist() {
        groupService.removeGroupBelongings(groupModel, TriggerSource.LOCAL)
        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
    }
}
