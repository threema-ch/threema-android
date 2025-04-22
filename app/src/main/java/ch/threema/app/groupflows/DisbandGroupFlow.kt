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
import ch.threema.app.tasks.OutgoingGroupDisbandTask
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

private val logger = LoggingUtil.getThreemaLogger("DisbandGroupFlow")

private const val DIALOG_TAG_DISBAND_GROUP = "disbandGroup"

/**
 * The intent of the group leave.
 */
enum class GroupDisbandIntent {
    /**
     * The user wants to disband the group.
     */
    DISBAND,

    /**
     * The user wants to disband and remove the group from the device.
     */
    DISBAND_AND_REMOVE,
}

/**
 * This class is used to disband a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runDisbandGroupFlow] instead of running this task directly. This ensures
 * that group flows are not executed concurrently.
 */
class DisbandGroupFlow(
    private val fragmentManager: FragmentManager?,
    private val intent: GroupDisbandIntent,
    private val groupModel: GroupModel,
    private val groupModelRepository: GroupModelRepository,
    private val groupCallManager: GroupCallManager,
    private val apiConnector: APIConnector,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val taskManager: TaskManager,
) : BackgroundTask<Boolean> {
    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.identity }

    private val groupService by lazy { outgoingCspMessageServices.groupService }

    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    override fun runBefore() {
        fragmentManager?.let {
            GenericProgressDialog.newInstance(R.string.updating_group, R.string.please_wait)
                .show(it, DIALOG_TAG_DISBAND_GROUP)
        }
    }

    override fun runInBackground(): Boolean {
        logger.info("Running disband group flow with intent {}", intent)

        val groupModelData = groupModel.data.value
        if (groupModelData == null) {
            logger.warn("Cannot disband already deleted group")
            return false
        }

        if (groupModel.groupIdentity.creatorIdentity != myIdentity) {
            logger.error("Cannot disband group where user is not creator")
            return false
        }

        if (groupModel.data.value?.isMember != true) {
            logger.error("Cannot disband already disbanded group")
            return false
        }

        // First reflect the changes
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
                    logger.info("Reflected group disband of intent {}", intent)
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
                DIALOG_TAG_DISBAND_GROUP,
                true,
            )
        }
    }

    private fun reflect() = runBlocking {
        when (intent) {
            GroupDisbandIntent.DISBAND ->
                taskManager.schedule(
                    ReflectLocalGroupLeaveOrDisband(
                        groupModel,
                        outgoingCspMessageServices.nonceFactory,
                        multiDeviceManager,
                    ),
                )

            GroupDisbandIntent.DISBAND_AND_REMOVE ->
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
            GroupDisbandIntent.DISBAND -> {
                groupModel.persistUserState(UserState.LEFT)
                groupService.runRejectedMessagesRefreshSteps(groupModel)
            }

            GroupDisbandIntent.DISBAND_AND_REMOVE -> {
                groupService.removeGroupBelongings(groupModel, TriggerSource.LOCAL)
                groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
            }
        }
    }

    private fun sendCsp(members: Set<String>) {
        // Schedule persistent task to send out csp group setup messages
        taskManager.schedule(
            OutgoingGroupDisbandTask(
                groupModel.groupIdentity,
                members,
                MessageId(),
                groupModelRepository,
                apiConnector,
                outgoingCspMessageServices,
            ),
        )
    }
}
