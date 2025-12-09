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

import ch.threema.app.managers.ListenerManager
import ch.threema.app.profilepicture.CheckedProfilePicture
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.protocol.ExpectedProfilePictureChange
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.tasks.GroupUpdateTask
import ch.threema.app.tasks.ReflectLocalGroupUpdate
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("UpdateGroupFlow")

class GroupChanges(
    /**
     * The updated group name. null if the group name should not be changed.
     */
    val name: String?,
    /**
     * The profile picture change.
     */
    val profilePictureChange: ProfilePictureChange,
    /**
     * The members that should be added to the group.
     */
    val addMembers: Set<String>,
    /**
     * The members that should be removed from the group.
     */
    val removeMembers: Set<String>,
) {
    /**
     * Get the group changes based on the given parameters.
     *
     * @param name the new name, null if not changed
     * @param profilePictureChange the profile picture change
     * @param updatedMembers the updated members *not* including the user
     * @param groupModelData the current snapshot of the group
     */
    constructor(
        name: String?,
        profilePictureChange: ProfilePictureChange,
        updatedMembers: Set<String>,
        groupModelData: GroupModelData,
    ) : this(
        name = name,
        profilePictureChange = profilePictureChange,
        addMembers = updatedMembers - groupModelData.otherMembers,
        removeMembers = groupModelData.otherMembers - updatedMembers,
    )

    sealed interface ProfilePictureChange {
        data class Set(val profilePicture: CheckedProfilePicture) : ProfilePictureChange
        object Remove : ProfilePictureChange
        object NoChange : ProfilePictureChange
    }
}

/**
 * This class is used to update a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runUpdateGroupFlow] instead of running this task directly. This ensures that
 * group flows are not executed concurrently.
 */
class UpdateGroupFlow(
    private val groupChanges: GroupChanges,
    private val groupModel: GroupModel,
    private val groupModelRepository: GroupModelRepository,
    private val groupCallManager: GroupCallManager,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupProfilePictureUploader: GroupProfilePictureUploader,
    private val fileService: FileService,
    private val taskManager: TaskManager,
    private val connection: ServerConnection,
) : BackgroundTask<GroupFlowResult> {
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.getIdentity() }

    override fun runInBackground(): GroupFlowResult {
        logger.info("Running update group flow")
        val groupModelData = groupModel.data ?: run {
            logger.warn("Cannot edit group where data is null")
            return GroupFlowResult.Failure.Other
        }

        if (groupModelData.groupIdentity.creatorIdentity != myIdentity) {
            logger.error("Group cannot be edited as the user is not the creator")
            return GroupFlowResult.Failure.Other
        }

        val groupFlowResult = if (multiDeviceManager.isMultiDeviceActive) {
            if (connection.connectionState != ConnectionState.LOGGEDIN) {
                return GroupFlowResult.Failure.Network
            }
            runBlocking {
                val reflectionResult: ReflectionResult<GroupProfilePictureUploadResult?> = taskManager.schedule(
                    ReflectLocalGroupUpdate(
                        updatedName = groupChanges.name,
                        addMembers = groupChanges.addMembers,
                        removeMembers = groupChanges.removeMembers,
                        profilePictureChange = groupChanges.profilePictureChange,
                        uploadGroupProfilePicture = ::uploadGroupProfilePicture,
                        groupModel = groupModel,
                        nonceFactory = outgoingCspMessageServices.nonceFactory,
                        contactModelRepository = outgoingCspMessageServices.contactModelRepository,
                        multiDeviceManager = multiDeviceManager,
                    ),
                ).await()

                when (reflectionResult) {
                    is ReflectionResult.Success -> {
                        when (val uploadResult = reflectionResult.result) {
                            is GroupProfilePictureUploadResult.Success -> {
                                finishGroupUpdate(ExpectedProfilePictureChange.Set.WithUpload(uploadResult))
                            }

                            null -> {
                                finishGroupUpdate(null)
                            }

                            is GroupProfilePictureUploadResult.Failure.UploadFailed -> {
                                logger.warn("Could not update group because blob server is not reachable")
                                GroupFlowResult.Failure.Network
                            }

                            is GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid -> {
                                logger.warn("Could not update group because onprem auth token is invalid")
                                GroupFlowResult.Failure.Other
                            }
                        }
                    }

                    is ReflectionResult.PreconditionFailed -> {
                        logger.warn("Precondition for updating group failed", reflectionResult.transactionException)
                        GroupFlowResult.Failure.Other
                    }

                    is ReflectionResult.Failed -> {
                        logger.error("Group update failed", reflectionResult.exception)
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
        } else {
            if (groupModelData.otherMembers.isEmpty() && groupChanges.addMembers.isEmpty()) {
                // Do not upload profile picture in notes groups
                val expectedProfilePictureChange = when (groupChanges.profilePictureChange) {
                    is GroupChanges.ProfilePictureChange.Set ->
                        ExpectedProfilePictureChange.Set.WithoutUpload(groupChanges.profilePictureChange.profilePicture)

                    is GroupChanges.ProfilePictureChange.Remove -> ExpectedProfilePictureChange.Remove
                    is GroupChanges.ProfilePictureChange.NoChange -> null
                }

                return finishGroupUpdate(expectedProfilePictureChange)
            }

            val expectedProfilePictureChange =
                when (val groupProfilePictureUploadResult = uploadGroupProfilePicture(groupChanges.profilePictureChange)) {
                    is GroupProfilePictureUploadResult.Success -> ExpectedProfilePictureChange.Set.WithUpload(groupProfilePictureUploadResult)
                    null -> {
                        when (groupChanges.profilePictureChange) {
                            is GroupChanges.ProfilePictureChange.Set -> {
                                logger.error("Group profile picture hasn't been uploaded but should have been uploaded")
                                // We can continue anyways, as the active group update steps will upload it in this case
                                ExpectedProfilePictureChange.Set.WithoutUpload(groupChanges.profilePictureChange.profilePicture)
                            }

                            is GroupChanges.ProfilePictureChange.Remove -> ExpectedProfilePictureChange.Remove
                            is GroupChanges.ProfilePictureChange.NoChange -> null
                        }
                    }

                    GroupProfilePictureUploadResult.Failure.UploadFailed -> return GroupFlowResult.Failure.Network
                    GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid -> return GroupFlowResult.Failure.Other
                }

            finishGroupUpdate(expectedProfilePictureChange)
        }

        return groupFlowResult
    }

    private fun uploadGroupProfilePicture(profilePictureChange: GroupChanges.ProfilePictureChange): GroupProfilePictureUploadResult? =
        if (profilePictureChange is GroupChanges.ProfilePictureChange.Set) {
            groupProfilePictureUploader.tryUploadingGroupProfilePicture(profilePictureChange.profilePicture)
        } else {
            null
        }

    private fun finishGroupUpdate(expectedProfilePictureChange: ExpectedProfilePictureChange?): GroupFlowResult {
        groupCallManager.removeGroupCallParticipants(groupChanges.removeMembers, groupModel)

        persistChanges()

        if (groupChanges.addMembers.isNotEmpty() || groupChanges.removeMembers.isNotEmpty()) {
            outgoingCspMessageServices.groupService.runRejectedMessagesRefreshSteps(groupModel)
        }

        val members = groupModel.data?.otherMembers ?: run {
            logger.error("Group model data expected to exist")
            return GroupFlowResult.Failure.Other
        }

        taskManager.schedule(
            GroupUpdateTask(
                name = groupChanges.name,
                expectedProfilePictureChange = expectedProfilePictureChange,
                updatedMembers = members,
                addedMembers = groupChanges.addMembers,
                removedMembers = groupChanges.removeMembers,
                groupIdentity = groupModel.groupIdentity,
                predefinedMessageIds = PredefinedMessageIds.random(),
                outgoingCspMessageServices = outgoingCspMessageServices,
                groupCallManager = groupCallManager,
                fileService = fileService,
                groupProfilePictureUploader = groupProfilePictureUploader,
                groupModelRepository = groupModelRepository,
            ),
        )

        return GroupFlowResult.Success(groupModel)
    }

    private fun persistChanges() {
        groupModel.persistMemberChanges(groupChanges.addMembers, groupChanges.removeMembers)

        val groupProfilePictureHasChanged = when (groupChanges.profilePictureChange) {
            is GroupChanges.ProfilePictureChange.Set -> {
                logger.info("Set profile picture")
                val oldGroupProfilePicture = fileService.getGroupProfilePictureBytes(groupModel)
                fileService.writeGroupProfilePicture(
                    groupModel,
                    groupChanges.profilePictureChange.profilePicture.bytes,
                )
                !groupChanges.profilePictureChange.profilePicture.bytes.contentEquals(oldGroupProfilePicture)
            }

            is GroupChanges.ProfilePictureChange.Remove -> {
                logger.info("Remove profile picture")
                val hadGroupProfilePicture = fileService.hasGroupProfilePicture(groupModel)
                fileService.removeGroupProfilePicture(groupModel)
                hadGroupProfilePicture
            }

            is GroupChanges.ProfilePictureChange.NoChange -> false
        }
        if (groupProfilePictureHasChanged) {
            logger.info("Group profile picture has changed")
            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
            ShortcutUtil.updateShareTargetShortcut(
                outgoingCspMessageServices.groupService.createReceiver(groupModel),
            )
        }

        groupChanges.name?.let { groupModel.persistName(it) }
    }
}
