package ch.threema.app.tasks

import ch.threema.app.profilepicture.CheckedProfilePicture
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.protocolsteps.ExpectedProfilePictureChange
import ch.threema.app.protocolsteps.PredefinedMessageIds
import ch.threema.app.services.FileService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.common.Image
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.group
import com.google.protobuf.kotlin.toByteString
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ConvertGroupProfilePictureTask")

/**
 * This task will convert the group profile picture to a jpeg and synchronize this change in case MD is active.
 */
class ConvertGroupProfilePictureTask(
    private val groupIdentity: GroupIdentity,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val fileService: FileService by inject()
    private val groupProfilePictureUploader: GroupProfilePictureUploader by inject()
    private val taskManager: TaskManager by inject()
    private val outgoingCspMessageServices: OutgoingCspMessageServices by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val nonceFactory: NonceFactory by inject()
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

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

        val groupProfilePictureBytes = fileService.getGroupProfilePictureBytes(groupModel) ?: run {
            logger.info("No group picture set. No conversion needed.")
            return
        }

        val convertedProfilePicture = CheckedProfilePicture.getOrConvertFromBytes(groupProfilePictureBytes)

        if (convertedProfilePicture?.bytes?.contentEquals(groupProfilePictureBytes) == true) {
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
            val uploadResult = groupProfilePictureUploader.tryUploadingGroupProfilePicture(updatedProfilePicture)

            when (uploadResult) {
                is GroupProfilePictureUploadResult.Success -> Unit
                is GroupProfilePictureUploadResult.Failure -> throw ProtocolException("Could not upload group profile picture")
            }

            logger.info("Reflecting group update to reflect group profile picture")
            handle.reflectAndAwaitAck(
                getEncryptedGroupSyncUpdate(
                    group = group {
                        groupIdentity = groupModel.groupIdentity.toProtobuf()
                        profilePicture = deltaImage {
                            updated = image {
                                type = Image.Type.JPEG
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
                storeD2dNonce = true,
                nonceFactory = nonceFactory,
            )
            uploadResult
        }
        persistGroupProfilePicture(groupModel, updatedProfilePicture)
        schedulePersistentTaskToRunActiveGroupUpdateSteps(
            groupModel = groupModel,
            expectedProfilePictureChange = ExpectedProfilePictureChange.Set.WithUpload(
                profilePictureUploadResultSuccess = uploadResult,
            ),
        )
    }

    private suspend fun removeGroupProfilePicture(groupModel: GroupModel, handle: ActiveTaskCodec) {
        logger.warn("Cannot convert group profile picture. Removing instead.")

        if (!multiDeviceManager.isMultiDeviceActive) {
            fileService.removeGroupProfilePicture(groupModel)
            return
        }

        handle.runInGroupSyncTransaction(groupModel) {
            logger.info("Reflecting group update to remove group profile picture")
            handle.reflectAndAwaitAck(
                encryptedEnvelopeResult = getEncryptedGroupSyncUpdate(
                    group = group {
                        groupIdentity = groupModel.groupIdentity.toProtobuf()
                        profilePicture = deltaImage {
                            removed = unit { }
                        }
                    },
                    memberStateChanges = emptyMap(),
                    multiDeviceProperties = multiDeviceManager.propertiesProvider.get(),
                ),
                storeD2dNonce = true,
                nonceFactory = nonceFactory,
            )
        }

        fileService.removeGroupProfilePicture(groupModel)
        schedulePersistentTaskToRunActiveGroupUpdateSteps(
            groupModel = groupModel,
            expectedProfilePictureChange = ExpectedProfilePictureChange.Remove,
        )
    }

    private fun persistGroupProfilePicture(groupModel: GroupModel, checkedProfilePicture: CheckedProfilePicture) {
        logger.info("Persisting profile picture")
        fileService.writeGroupProfilePicture(groupModel, checkedProfilePicture.bytes)
    }

    private fun schedulePersistentTaskToRunActiveGroupUpdateSteps(
        groupModel: GroupModel,
        expectedProfilePictureChange: ExpectedProfilePictureChange,
    ) {
        taskManager.schedule(
            GroupUpdateTask(
                name = null,
                expectedProfilePictureChange = expectedProfilePictureChange,
                updatedMembers = emptySet(),
                addedMembers = emptySet(),
                removedMembers = emptySet(),
                groupIdentity = groupModel.groupIdentity,
                predefinedMessageIds = PredefinedMessageIds.random(),
            ),
        )
        logger.info("Scheduled persistent task to run the active group update steps")
    }

    private suspend fun <T> ActiveTaskCodec.runInGroupSyncTransaction(groupModel: GroupModel, runInTransaction: suspend () -> T): T =
        createTransaction(
            keys = multiDeviceManager.propertiesProvider.get().keys,
            scope = TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = {
                !groupModel.isDeleted
            },
        ).execute(runInTransaction)

    override fun serialize() = ConvertGroupProfilePictureTaskData(groupIdentity)

    @Serializable
    data class ConvertGroupProfilePictureTaskData(private val groupIdentity: GroupIdentity) : SerializableTaskData {
        override fun createTask() = ConvertGroupProfilePictureTask(groupIdentity)
    }
}
