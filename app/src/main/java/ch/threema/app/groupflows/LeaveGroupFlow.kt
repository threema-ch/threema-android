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

import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.tasks.OutgoingGroupLeaveTask
import ch.threema.app.tasks.ReflectGroupSyncDeletePrecondition
import ch.threema.app.tasks.ReflectGroupSyncDeleteTask
import ch.threema.app.tasks.ReflectLocalGroupLeaveOrDisband
import ch.threema.app.tasks.ReflectionFailed
import ch.threema.app.tasks.ReflectionPreconditionFailed
import ch.threema.app.tasks.ReflectionSuccess
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupModel.UserState
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("LeaveGroupFlow")

private const val DIALOG_TAG_LEAVE_GROUP = "groupLeave"

/**
 * The intent of the group leave.
 */
enum class GroupLeaveIntent {
    /**
     * The user wants to leave the group.
     */
    LEAVE,

    /**
     * The user wants to leave and remove the group from the device.
     */
    LEAVE_AND_REMOVE,
}

/**
 * This class is used to leave a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runLeaveGroupFlow] instead of running this task directly. This ensures that
 * group flows are not executed concurrently.
 */
class LeaveGroupFlow(
    private val fragmentManager: FragmentManager?,
    private val intent: GroupLeaveIntent,
    private val groupModel: GroupModel,
    private val groupModelRepository: GroupModelRepository,
    private val groupCallManager: GroupCallManager,
    private val apiConnector: APIConnector,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val taskManager: TaskManager,
) : BackgroundTask<Boolean> {
    private val groupService by lazy { outgoingCspMessageServices.groupService }

    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.identity }

    override fun runBefore() {
        fragmentManager?.let {
            GenericProgressDialog.newInstance(R.string.updating_group, R.string.please_wait)
                .show(it, DIALOG_TAG_LEAVE_GROUP)
        }
    }

    override fun runInBackground(): Boolean {
        logger.info("Running leave group flow with intent {}", intent)

        if (groupModel.groupIdentity.creatorIdentity == myIdentity) {
            logger.error("Cannot leave group where the user is the creator")
            return false
        }

        val groupModelData = groupModel.data.value
        if (groupModelData == null) {
            logger.error("Cannot leave deleted group")
            return false
        }

        if (!groupModelData.isMember) {
            logger.error("Cannot leave already left/kicked group")
            return false
        }

        // First, reflect the leave (if md is active)
        if (multiDeviceManager.isMultiDeviceActive) {
            when (val reflectionResult = reflect()) {
                is ReflectionFailed -> {
                    logger.error("Reflection failed", reflectionResult.exception)
                    return false
                }

                is ReflectionPreconditionFailed -> {
                    logger.warn(
                        "Reflection failed due to precondition",
                        reflectionResult.transactionException,
                    )
                    return false
                }

                is ReflectionSuccess -> {
                    logger.info("Reflected group leave of intent {}", intent)
                }
            }
        }

        // Then persist the changes locally
        persist()

        // Finally, send the csp messages to the group members
        sendCsp(groupModelData.otherMembers)

        return true
    }

    override fun runAfter(result: Boolean) {
        fragmentManager?.let {
            DialogUtil.dismissDialog(
                it,
                DIALOG_TAG_LEAVE_GROUP,
                true,
            )
        }
    }

    private fun reflect() = runBlocking {
        when (intent) {
            GroupLeaveIntent.LEAVE ->
                taskManager.schedule(
                    ReflectLocalGroupLeaveOrDisband(
                        groupModel,
                        outgoingCspMessageServices.nonceFactory,
                        multiDeviceManager,
                    ),
                )

            GroupLeaveIntent.LEAVE_AND_REMOVE ->
                taskManager.schedule(
                    ReflectGroupSyncDeleteTask(
                        groupModel,
                        ReflectGroupSyncDeletePrecondition.USER_IS_MEMBER,
                        outgoingCspMessageServices.nonceFactory,
                        multiDeviceManager,
                    ),
                )
        }.await()
    }

    private fun persist() {
        groupCallManager.removeGroupCallParticipants(setOf(myIdentity), groupModel)

        when (intent) {
            GroupLeaveIntent.LEAVE -> {
                groupModel.persistUserState(UserState.LEFT)
                groupService.runRejectedMessagesRefreshSteps(groupModel)
            }

            GroupLeaveIntent.LEAVE_AND_REMOVE -> {
                groupService.removeGroupBelongings(groupModel, TriggerSource.LOCAL)
                groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
            }
        }
    }

    private fun sendCsp(members: Set<String>) {
        // Schedule persistent task to send out csp leave messages
        taskManager.schedule(
            OutgoingGroupLeaveTask(
                groupModel.groupIdentity,
                MessageId(),
                members,
                groupModelRepository,
                apiConnector,
                outgoingCspMessageServices,
            ),
        )
    }
}
