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
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.tasks.GroupPhotoUploadResult
import ch.threema.app.tasks.GroupUpdateTask
import ch.threema.app.tasks.ReflectLocalGroupUpdate
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.tasks.tryUploadingGroupPhoto
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("UpdateGroupFlow")

class GroupChanges(
    /**
     * The updated group name. null if the group name should not be changed.
     */
    val name: String?,
    /**
     * The profile picture change.
     */
    val profilePictureChange: ProfilePictureChange?,
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
        profilePictureChange: ProfilePictureChange?,
        updatedMembers: Set<String>,
        groupModelData: GroupModelData,
    ) : this(
        name = name,
        profilePictureChange = profilePictureChange,
        addMembers = updatedMembers - groupModelData.otherMembers,
        removeMembers = groupModelData.otherMembers - updatedMembers,
    )
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
    private val apiService: ApiService,
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
                val reflectionResult: ReflectionResult<Unit> = taskManager.schedule(
                    ReflectLocalGroupUpdate(
                        updatedName = groupChanges.name,
                        addMembers = groupChanges.addMembers,
                        removeMembers = groupChanges.removeMembers,
                        profilePictureChange = groupChanges.profilePictureChange,
                        uploadGroupPhoto = ::uploadGroupPicture,
                        finishGroupUpdate = ::finishGroupUpdate,
                        groupModel = groupModel,
                        nonceFactory = outgoingCspMessageServices.nonceFactory,
                        contactModelRepository = outgoingCspMessageServices.contactModelRepository,
                        multiDeviceManager = multiDeviceManager,
                    ),
                ).await()

                when (reflectionResult) {
                    is ReflectionResult.Success -> {
                        logger.info("Group update successful")
                        GroupFlowResult.Success(groupModel)
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
            val groupPhotoUploadResult =
                runCatching {
                    uploadGroupPicture(groupChanges.profilePictureChange)
                }.getOrElse { exception ->
                    logger.error("Failed to upload the group photo", exception)
                    // While this could also have internal reasons, a network failure is more likely
                    // TODO(ANDR-3823): Distinguish between different failures from uploading process
                    return GroupFlowResult.Failure.Network
                }
            finishGroupUpdate(groupPhotoUploadResult)
        }

        return groupFlowResult
    }

    private fun uploadGroupPicture(profilePictureChange: ProfilePictureChange?): GroupPhotoUploadResult? =
        if (profilePictureChange is SetProfilePicture) {
            tryUploadingGroupPhoto(profilePictureChange.profilePicture.profilePictureBytes, apiService)
        } else {
            null
        }

    /**
     *  TODO(ANDR-3823): Remove this warning once corrected
     *
     *  Warning: At this point, before `ANDR-3823` is implemented, *any* specific type of [GroupFlowResult.Failure]
     *  returned from this method will effectively result in a [GroupFlowResult.Failure.Other] *if MD is active*.
     */
    private fun finishGroupUpdate(groupPhotoUploadResult: GroupPhotoUploadResult?): GroupFlowResult {
        if (groupChanges.profilePictureChange is SetProfilePicture && groupPhotoUploadResult == null) {
            logger.error("Group photo upload result must not be null. Continuing anyway.")
        }

        groupCallManager.removeGroupCallParticipants(groupChanges.removeMembers, groupModel)

        persistChanges()

        if (groupChanges.addMembers.isNotEmpty() || groupChanges.removeMembers.isNotEmpty()) {
            outgoingCspMessageServices.groupService.runRejectedMessagesRefreshSteps(groupModel)
        }

        val profilePictureChange =
            when (groupChanges.profilePictureChange) {
                is SetProfilePicture -> SetProfilePicture(groupChanges.profilePictureChange.profilePicture, groupPhotoUploadResult)
                else -> RemoveProfilePicture
            }

        val members = groupModel.data?.otherMembers ?: run {
            logger.error("Group model data expected to exist")
            return GroupFlowResult.Failure.Other
        }

        taskManager.schedule(
            GroupUpdateTask(
                groupChanges.name,
                profilePictureChange,
                members,
                groupChanges.addMembers,
                groupChanges.removeMembers,
                groupModel.groupIdentity,
                PredefinedMessageIds(),
                outgoingCspMessageServices,
                groupCallManager,
                fileService,
                apiService,
                groupModelRepository,
            ),
        )

        return GroupFlowResult.Success(groupModel)
    }

    private fun persistChanges() {
        groupModel.persistMemberChanges(groupChanges.addMembers, groupChanges.removeMembers)

        val avatarHasChanged = when (groupChanges.profilePictureChange) {
            is SetProfilePicture -> {
                logger.info("Set profile picture")
                val oldGroupAvatar = fileService.getGroupAvatarBytes(groupModel)
                fileService.writeGroupAvatar(
                    groupModel,
                    groupChanges.profilePictureChange.profilePicture.profilePictureBytes,
                )
                !groupChanges.profilePictureChange.profilePicture.profilePictureBytes.contentEquals(oldGroupAvatar)
            }

            is RemoveProfilePicture -> {
                logger.info("Remove profile picture")
                val hadAvatar = fileService.hasGroupAvatarFile(groupModel)
                fileService.removeGroupAvatar(groupModel)
                hadAvatar
            }

            null -> false
        }
        if (avatarHasChanged) {
            logger.info("Avatar has changed")
            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
            ShortcutUtil.updateShareTargetShortcut(
                outgoingCspMessageServices.groupService.createReceiver(groupModel),
            )
        }

        groupChanges.name?.let { groupModel.persistName(it) }
    }
}
