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

import android.content.Context
import ch.threema.app.profilepicture.CheckedProfilePicture
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.protocol.ExpectedProfilePictureChange
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.ReflectGroupSyncCreateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.common.secureRandom
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.runCatchingExceptNetworkException
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("CreateGroupFlow")

class GroupCreateProperties(
    val name: String,
    val profilePicture: CheckedProfilePicture?,
    val members: Set<String>,
)

/**
 * This class is used to create a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runCreateGroupFlow] instead of running this task directly. This ensures that
 * group flows are not executed concurrently.
 */
class CreateGroupFlow(
    private val context: Context,
    private val groupCreateProperties: GroupCreateProperties,
    private val groupModelRepository: GroupModelRepository,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupCallManager: GroupCallManager,
    private val groupProfilePictureUploader: GroupProfilePictureUploader,
    private val fileService: FileService,
    private val taskManager: TaskManager,
    private val connection: ServerConnection,
) : BackgroundTask<GroupFlowResult> {
    private val contactModelRepository = outgoingCspMessageServices.contactModelRepository
    private val multiDeviceManager = outgoingCspMessageServices.multiDeviceManager
    private val nonceFactory = outgoingCspMessageServices.nonceFactory

    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.getIdentity()!! }

    private val groupModelData by lazy {
        val now = now()
        GroupModelData(
            groupIdentity = GroupIdentity(
                myIdentity,
                secureRandom().nextLong(),
            ),
            name = groupCreateProperties.name,
            createdAt = now,
            synchronizedAt = null,
            lastUpdate = now,
            isArchived = false,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            otherMembers = groupCreateProperties.members - myIdentity,
            userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
            notificationTriggerPolicyOverride = null,
        )
    }

    override fun runInBackground(): GroupFlowResult {
        if (ConfigUtils.isWorkBuild() && AppRestrictionUtil.isCreateGroupDisabled(context)) {
            return GroupFlowResult.Failure.Other
        }
        val groupFlowResult: GroupFlowResult = if (multiDeviceManager.isMultiDeviceActive) {
            createGroupWithMd()
        } else {
            createGroupWithoutMd()
        }

        return groupFlowResult
    }

    private fun createGroupWithoutMd(): GroupFlowResult {
        if (groupModelData.otherMembers.isEmpty()) {
            // In case of a notes group, we just create the group locally without uploading the profile picture
            val expectedProfilePictureChange = if (groupCreateProperties.profilePicture != null) {
                ExpectedProfilePictureChange.Set.WithoutUpload(groupCreateProperties.profilePicture)
            } else {
                ExpectedProfilePictureChange.Remove
            }

            return createGroupLocally(expectedProfilePictureChange)?.let { groupModel -> GroupFlowResult.Success(groupModel) }
                ?: GroupFlowResult.Failure.Other
        }

        return when (val uploadGroupPictureResult = uploadGroupPicture()) {
            is GroupProfilePictureUploadResult.Success, null -> {
                val expectedProfilePictureChange = uploadGroupPictureResult.toProfilePictureChange() ?: ExpectedProfilePictureChange.Remove
                createGroupLocally(expectedProfilePictureChange)?.let { groupModel ->
                    GroupFlowResult.Success(groupModel)
                } ?: GroupFlowResult.Failure.Other
            }

            GroupProfilePictureUploadResult.Failure.UploadFailed -> GroupFlowResult.Failure.Network

            GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid -> GroupFlowResult.Failure.Other
        }
    }

    private fun createGroupWithMd(): GroupFlowResult {
        if (connection.connectionState != ConnectionState.LOGGEDIN) {
            return GroupFlowResult.Failure.Network
        }
        return runBlocking {
            val reflectionResult: ReflectionResult<GroupProfilePictureUploadResult?> = taskManager.schedule(
                ReflectGroupSyncCreateTask(
                    groupModelData = groupModelData,
                    contactModelRepository = contactModelRepository,
                    groupModelRepository = groupModelRepository,
                    nonceFactory = nonceFactory,
                    uploadGroupProfilePicture = ::uploadGroupPicture,
                    multiDeviceManager = multiDeviceManager,
                ),
            ).await()
            when (reflectionResult) {
                is ReflectionResult.Success -> {
                    when (val uploadResult = reflectionResult.result) {
                        is GroupProfilePictureUploadResult.Success, null -> {
                            val groupModel = createGroupLocally(uploadResult.toProfilePictureChange() ?: ExpectedProfilePictureChange.Remove)
                            if (groupModel != null) {
                                GroupFlowResult.Success(groupModel)
                            } else {
                                GroupFlowResult.Failure.Other
                            }
                        }

                        is GroupProfilePictureUploadResult.Failure.UploadFailed -> {
                            logger.warn("Could not create group because the blob upload failed")
                            GroupFlowResult.Failure.Network
                        }

                        is GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid -> {
                            logger.warn("Could not create group because onprem auth token is invalid")
                            GroupFlowResult.Failure.Other
                        }
                    }
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.error("Reflection of group sync create failed due to the precondition", reflectionResult.transactionException)
                    GroupFlowResult.Failure.Other
                }

                is ReflectionResult.Failed -> {
                    logger.error("Reflection of group sync create failed", reflectionResult.exception)
                    GroupFlowResult.Failure.Other
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is a very rare edge case that should not be possible at all. If it happens, we need to return a failure as
                    // the changes are not persisted and not sent to the members.
                    logger.warn("Reflection failed because multi device is not active")
                    GroupFlowResult.Failure.Other
                }
            }
        }
    }

    private fun uploadGroupPicture(): GroupProfilePictureUploadResult? {
        return groupCreateProperties.profilePicture?.let { profilePicture ->
            groupProfilePictureUploader.tryUploadingGroupProfilePicture(profilePicture)
        }
    }

    /**
     * Create group locally. If this fails, null is returned.
     *
     * @param expectedProfilePictureChange in case the expectation is that a profile picture will be set, the given profile picture will be persisted.
     * In case the expectation is that a profile picture will be removed, nothing needs to be done as there is no profile picture because the group
     * hasn't existed before. The expectation will be checked after the group has been created locally to log sync races (if MD is active).
     */
    private fun createGroupLocally(expectedProfilePictureChange: ExpectedProfilePictureChange): GroupModel? {
        // Persist the new group
        val groupModel = groupModelRepository.persistNewGroup(groupModelData)

        // Persist the group profile picture
        when (expectedProfilePictureChange) {
            is ExpectedProfilePictureChange.Set -> expectedProfilePictureChange.profilePicture
            is ExpectedProfilePictureChange.Remove -> null
        }?.let { profilePicture ->
            runCatchingExceptNetworkException {
                fileService.writeGroupProfilePicture(
                    groupModel,
                    profilePicture.bytes,
                )
            }.onFailure { throwable ->
                logger.error("Could not persist group profile picture", throwable)
            }
        }

        // In case there are no members, we are done and do not need to send any csp messages.
        if (groupCreateProperties.members.isEmpty()) {
            return groupModel
        }

        val groupModelData = groupModel.data ?: run {
            logger.error("Group model data expected to exist")
            return null
        }

        // Can be suppressed because we do not wait for the task to complete
        @Suppress("DeferredResultUnused")
        taskManager.schedule(
            // We schedule the group create task to send the csp messages to the members to inform them about the group.
            task = GroupCreateTask(
                name = groupModelData.name,
                expectedProfilePictureChange = expectedProfilePictureChange,
                members = groupModelData.otherMembers,
                groupIdentity = groupModelData.groupIdentity,
                predefinedMessageIds = PredefinedMessageIds.random(),
                outgoingCspMessageServices = outgoingCspMessageServices,
                groupCallManager = groupCallManager,
                fileService = fileService,
                groupProfilePictureUploader = groupProfilePictureUploader,
                groupModelRepository = groupModelRepository,
            ),
        )

        return groupModel
    }

    private fun GroupProfilePictureUploadResult.Success?.toProfilePictureChange(): ExpectedProfilePictureChange? =
        this?.let {
            ExpectedProfilePictureChange.Set.WithUpload(
                profilePictureUploadResultSuccess = this,
            )
        }
}
