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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.app.protocol.runActiveGroupUpdateSteps
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("GroupUpdateTask")

class GroupUpdateTask(
    private val name: String?,
    private val profilePictureChange: ProfilePictureChange,
    private val updatedMembers: Set<String>,
    private val addedMembers: Set<String>,
    private val removedMembers: Set<String>,
    private val groupIdentity: GroupIdentity,
    private val predefinedMessageIds: PredefinedMessageIds,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupCallManager: GroupCallManager,
    private val fileService: FileService,
    private val apiService: ApiService,
    private val groupModelRepository: GroupModelRepository,
) : ActiveTask<Unit>, PersistableTask {
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    override val type = "GroupUpdateTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            try {
                executeActiveGroupUpdateInTransaction(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.warn("A group sync race occurred", e)
            }
        } else {
            executeActiveGroupUpdate(handle)
        }
    }

    private suspend fun executeActiveGroupUpdateInTransaction(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        handle.createTransaction(
            multiDeviceProperties.keys,
            MdD2D.TransactionScope.Scope.GROUP_SYNC,
            TRANSACTION_TTL_MAX,
            precondition = {
                groupModelRepository.getByGroupIdentity(groupIdentity)?.data?.let { it.isMember && it.otherMembers.isNotEmpty() } == true
            },
        ).execute {
            executeActiveGroupUpdate(handle)
        }
    }

    private suspend fun executeActiveGroupUpdate(handle: ActiveTaskCodec) {
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity)
        val groupModelData = groupModel?.data ?: run {
            logger.warn("Group sync race occurred: Group model does not exist")
            return
        }

        checkForGroupSyncRace(groupModel, groupModelData)
        runActiveGroupUpdateSteps(
            profilePictureChange,
            addedMembers,
            removedMembers,
            predefinedMessageIds,
            groupModel,
            outgoingCspMessageServices,
            groupCallManager,
            fileService,
            apiService,
            handle,
        )
    }

    private fun checkForGroupSyncRace(groupModel: GroupModel, groupModelData: GroupModelData) {
        if (name != null && groupModelData.name != name) {
            logger.warn("Group sync race occurred: Group name is not equal")
        }

        val persistedGroupAvatar = fileService.getGroupAvatarBytes(groupModel)
        when (profilePictureChange) {
            is SetProfilePicture -> {
                if (persistedGroupAvatar == null) {
                    logger.warn("Group sync race occurred: No group avatar is persisted")
                } else if (!persistedGroupAvatar.contentEquals(profilePictureChange.profilePicture.profilePictureBytes)) {
                    logger.warn("Group sync race occurred: Different group avatar is persisted")
                }
            }
            RemoveProfilePicture -> {
                if (persistedGroupAvatar != null) {
                    logger.warn("Group sync race occurred: Group avatar is persisted")
                }
            }
        }

        if (groupModelData.otherMembers != updatedMembers) {
            logger.warn("Group sync race occurred: Group members are not equal")
        }
    }

    override fun serialize(): SerializableTaskData = GroupUpdateTaskData(
        name,
        profilePictureChange,
        updatedMembers,
        addedMembers,
        removedMembers,
        groupIdentity,
        predefinedMessageIds,
    )

    @Serializable
    data class GroupUpdateTaskData(
        private val name: String?,
        private val profilePictureChange: ProfilePictureChange,
        private val updatedMembers: Set<String>,
        private val addedMembers: Set<String>,
        private val removedMembers: Set<String>,
        private val groupIdentity: GroupIdentity,
        private val predefinedMessageIds: PredefinedMessageIds,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            GroupUpdateTask(
                name,
                profilePictureChange,
                updatedMembers,
                addedMembers,
                removedMembers,
                groupIdentity,
                predefinedMessageIds,
                serviceManager.getOutgoingCspMessageServices(),
                serviceManager.groupCallManager,
                serviceManager.fileService,
                serviceManager.apiService,
                serviceManager.modelRepositories.groups,
            )
    }
}
