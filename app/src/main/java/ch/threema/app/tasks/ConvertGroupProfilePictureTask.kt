/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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
import ch.threema.app.profilepicture.CheckedProfilePicture
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.Common
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import com.google.protobuf.kotlin.toByteString
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("ConvertGroupProfilePictureTask")

/**
 * This task will convert the group profile picture to a jpeg and synchronize this change in case MD is active.
 */
class ConvertGroupProfilePictureTask(
    private val groupIdentity: GroupIdentity,
    private val fileService: FileService,
    private val apiService: ApiService,
    private val taskManager: TaskManager,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupCallManager: GroupCallManager,
    private val groupModelRepository: GroupModelRepository,
) : ActiveTask<Unit>, PersistableTask {
    private val multiDeviceManager = outgoingCspMessageServices.multiDeviceManager

    override val type = "ConvertGroupProfilePictureTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity) ?: run {
            logger.warn("Group model not found for the given group identity")
            return
        }

        if (!groupModel.isCreator()) {
            logger.warn("Cannot update group profile picture if the user is not the creator")
            return
        }

        val groupProfilePictureBytes = fileService.getGroupAvatarBytes(groupModel) ?: run {
            logger.info("No group picture set. No conversion needed.")
            return
        }

        val convertedProfilePicture = CheckedProfilePicture.getOrConvertFromBytes(groupProfilePictureBytes)

        if (convertedProfilePicture?.profilePictureBytes?.contentEquals(groupProfilePictureBytes) == true) {
            logger.info("No need to convert group profile picture")
            return
        }

        if (convertedProfilePicture != null) {
            updateGroupProfilePicture(
                updatedProfilePicture = convertedProfilePicture,
                groupModel = groupModel,
                handle = handle,
            )
        } else {
            removeGroupProfilePicture(groupModel, handle)
        }
    }

    private suspend fun updateGroupProfilePicture(
        groupModel: GroupModel,
        updatedProfilePicture: CheckedProfilePicture,
        handle: ActiveTaskCodec,
    ) {
        logger.info("Updating group profile picture")

        if (!multiDeviceManager.isMultiDeviceActive) {
            persistGroupProfilePicture(groupModel, updatedProfilePicture)
            return
        }

        val uploadResult = handle.runInGroupSyncTransaction(groupModel) {
            logger.info("Uploading group profile picture")
            val uploadResult = tryUploadingGroupPhoto(updatedProfilePicture.profilePictureBytes, apiService)

            logger.info("Reflecting group update to reflect group profile picture")
            handle.reflect(
                getEncryptedGroupSyncUpdate(
                    group = group {
                        groupIdentity = groupModel.groupIdentity.toProtobuf()
                        profilePicture = deltaImage {
                            updated = image {
                                type = Common.Image.Type.JPEG
                                blob = blob {
                                    id = uploadResult.blobId.toByteString()
                                    nonce = ProtocolDefines.GROUP_PHOTO_NONCE.toByteString()
                                    key = uploadResult.encryptionKey.toByteString()
                                }
                            }
                        }
                    },
                    memberStateChanges = emptyMap(),
                    multiDeviceProperties = multiDeviceManager.propertiesProvider.get(),
                ),
            )
            uploadResult
        }
        persistGroupProfilePicture(groupModel, updatedProfilePicture)
        schedulePersistentTaskToRunActiveGroupUpdateSteps(
            groupModel = groupModel,
            profilePictureChange = SetProfilePicture(
                profilePicture = updatedProfilePicture,
                profilePictureUploadResult = uploadResult,
            ),
        )
    }

    private suspend fun removeGroupProfilePicture(groupModel: GroupModel, handle: ActiveTaskCodec) {
        logger.warn("Cannot convert group profile picture. Removing instead.")

        if (!multiDeviceManager.isMultiDeviceActive) {
            fileService.removeGroupAvatar(groupModel)
            return
        }

        handle.runInGroupSyncTransaction(groupModel) {
            logger.info("Reflecting group update to remove group profile picture")
            handle.reflect(
                getEncryptedGroupSyncUpdate(
                    group = group {
                        groupIdentity = groupModel.groupIdentity.toProtobuf()
                        profilePicture = deltaImage {
                            removed = unit { }
                        }
                    },
                    memberStateChanges = emptyMap(),
                    multiDeviceProperties = multiDeviceManager.propertiesProvider.get(),
                ),
            )
        }

        fileService.removeGroupAvatar(groupModel)
        schedulePersistentTaskToRunActiveGroupUpdateSteps(
            groupModel = groupModel,
            profilePictureChange = RemoveProfilePicture,
        )
    }

    private fun persistGroupProfilePicture(groupModel: GroupModel, checkedProfilePicture: CheckedProfilePicture) {
        logger.info("Persisting profile picture")
        if (!fileService.writeGroupAvatar(groupModel, checkedProfilePicture.profilePictureBytes)) {
            logger.error("Could not write updated group profile picture")
        }
    }

    private fun schedulePersistentTaskToRunActiveGroupUpdateSteps(groupModel: GroupModel, profilePictureChange: ProfilePictureChange) {
        taskManager.schedule(
            GroupUpdateTask(
                name = null,
                profilePictureChange = profilePictureChange,
                updatedMembers = emptySet(),
                addedMembers = emptySet(),
                removedMembers = emptySet(),
                groupIdentity = groupModel.groupIdentity,
                predefinedMessageIds = PredefinedMessageIds(),
                outgoingCspMessageServices = outgoingCspMessageServices,
                groupCallManager = groupCallManager,
                fileService = fileService,
                apiService = apiService,
                groupModelRepository = groupModelRepository,
            ),
        )
        logger.info("Scheduled persistent task to run the active group update steps")
    }

    private suspend fun <T> ActiveTaskCodec.runInGroupSyncTransaction(groupModel: GroupModel, runInTransaction: suspend () -> T): T =
        createTransaction(
            keys = multiDeviceManager.propertiesProvider.get().keys,
            scope = MdD2D.TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = {
                !groupModel.isDeleted
            },
        ).execute(runInTransaction)

    override fun serialize() = ConvertGroupProfilePictureTaskData(groupIdentity)

    @Serializable
    data class ConvertGroupProfilePictureTaskData(private val groupIdentity: GroupIdentity) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager) = createFromServiceManager(groupIdentity, serviceManager)
    }

    companion object {
        fun createFromServiceManager(groupIdentity: GroupIdentity, serviceManager: ServiceManager) = ConvertGroupProfilePictureTask(
            groupIdentity = groupIdentity,
            fileService = serviceManager.fileService,
            apiService = serviceManager.apiService,
            taskManager = serviceManager.taskManager,
            outgoingCspMessageServices = OutgoingCspMessageServices(
                forwardSecurityMessageProcessor = serviceManager.forwardSecurityMessageProcessor,
                identityStore = serviceManager.identityStore,
                userService = serviceManager.userService,
                contactStore = serviceManager.contactStore,
                contactService = serviceManager.contactService,
                contactModelRepository = serviceManager.modelRepositories.contacts,
                groupService = serviceManager.groupService,
                nonceFactory = serviceManager.nonceFactory,
                blockedIdentitiesService = serviceManager.blockedIdentitiesService,
                preferenceService = serviceManager.preferenceService,
                multiDeviceManager = serviceManager.multiDeviceManager,
            ),
            groupCallManager = serviceManager.groupCallManager,
            groupModelRepository = serviceManager.modelRepositories.groups,
        )
    }
}
