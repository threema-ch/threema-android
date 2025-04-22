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
import ch.threema.app.tasks.ReflectionFailed
import ch.threema.app.tasks.ReflectionPreconditionFailed
import ch.threema.app.tasks.ReflectionSuccess
import ch.threema.app.tasks.tryUploadingGroupPhoto
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("UpdateGroupFlow")

private const val DIALOG_TAG_UPDATE_GROUP = "groupUpdate"

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
        name,
        profilePictureChange,
        updatedMembers - groupModelData.otherMembers,
        groupModelData.otherMembers - updatedMembers,
    )
}

/**
 * This class is used to update a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runUpdateGroupFlow] instead of running this task directly. This ensures that
 * group flows are not executed concurrently.
 */
class UpdateGroupFlow(
    private val fragmentManager: FragmentManager?,
    private val groupChanges: GroupChanges,
    private val groupModel: GroupModel,
    private val groupModelRepository: GroupModelRepository,
    private val groupCallManager: GroupCallManager,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val apiService: ApiService,
    private val fileService: FileService,
    private val taskManager: TaskManager,
) : BackgroundTask<Boolean> {
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.identity }

    override fun runBefore() {
        fragmentManager?.let {
            GenericProgressDialog.newInstance(R.string.updating_group, R.string.please_wait)
                .show(it, DIALOG_TAG_UPDATE_GROUP)
        }
    }

    override fun runInBackground(): Boolean {
        logger.info("Running update group flow")
        val groupModelData = groupModel.data.value ?: run {
            logger.warn("Cannot edit group where data is null")
            return false
        }

        if (groupModelData.groupIdentity.creatorIdentity != myIdentity) {
            logger.error("Group cannot be edited as the user is not the creator")
            return false
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            runBlocking {
                val reflectionResult = taskManager.schedule(
                    ReflectLocalGroupUpdate(
                        groupChanges.name,
                        groupChanges.addMembers,
                        groupChanges.removeMembers,
                        groupChanges.profilePictureChange,
                        uploadGroupPhoto = { p -> uploadGroupPicture(p) },
                        finishGroupUpdate = { finishGroupUpdate(it) },
                        groupModel,
                        outgoingCspMessageServices.nonceFactory,
                        outgoingCspMessageServices.contactModelRepository,
                        multiDeviceManager,
                    ),
                ).await()

                when (reflectionResult) {
                    is ReflectionSuccess -> logger.info("Group update successful")

                    is ReflectionPreconditionFailed -> logger.warn(
                        "Precondition for updating group failed",
                        reflectionResult.transactionException,
                    )

                    is ReflectionFailed -> logger.error(
                        "Group update failed",
                        reflectionResult.exception,
                    )
                }
            }
        } else {
            val groupPhotoUploadResult = uploadGroupPicture(groupChanges.profilePictureChange)
            finishGroupUpdate(groupPhotoUploadResult)
        }

        return true
    }

    override fun runAfter(result: Boolean) {
        fragmentManager?.let {
            DialogUtil.dismissDialog(
                it,
                DIALOG_TAG_UPDATE_GROUP,
                true,
            )
        }
    }

    private fun uploadGroupPicture(profilePictureChange: ProfilePictureChange?): GroupPhotoUploadResult? {
        return if (profilePictureChange is SetProfilePicture) {
            tryUploadingGroupPhoto(profilePictureChange.profilePicture, apiService)
        } else {
            null
        }
    }

    private fun finishGroupUpdate(groupPhotoUploadResult: GroupPhotoUploadResult?) {
        groupCallManager.removeGroupCallParticipants(groupChanges.removeMembers, groupModel)

        persistChanges()

        if (groupChanges.addMembers.isNotEmpty() || groupChanges.removeMembers.isNotEmpty()) {
            outgoingCspMessageServices.groupService.runRejectedMessagesRefreshSteps(groupModel)
        }

        val profilePictureChange = if (groupChanges.profilePictureChange is SetProfilePicture) {
            if (groupPhotoUploadResult == null) {
                logger.error("Group photo upload result must not be null")
            }
            SetProfilePicture(
                groupChanges.profilePictureChange.profilePicture,
                groupPhotoUploadResult,
            )
        } else {
            RemoveProfilePicture
        }

        val members = groupModel.data.value?.otherMembers ?: run {
            logger.error("Group model data expected to exist")
            return
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
    }

    private fun persistChanges() {
        groupModel.persistMemberChanges(groupChanges.addMembers, groupChanges.removeMembers)

        val avatarHasChanged = when (groupChanges.profilePictureChange) {
            is SetProfilePicture -> {
                logger.info("Set profile picture")
                val oldGroupAvatar = fileService.getGroupAvatarBytes(groupModel)
                fileService.writeGroupAvatar(
                    groupModel,
                    groupChanges.profilePictureChange.profilePicture,
                )
                !groupChanges.profilePictureChange.profilePicture.contentEquals(oldGroupAvatar)
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
