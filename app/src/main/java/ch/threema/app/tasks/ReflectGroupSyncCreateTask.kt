package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.protocol.ExpectedProfilePictureChange
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedGroupSyncCreate
import ch.threema.protobuf.d2d.sync.MdD2DSync

private val logger = getThreemaLogger("ReflectGroupSyncCreateTask")

class ReflectGroupSyncCreateTask(
    private val groupModelData: GroupModelData,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val nonceFactory: NonceFactory,
    private val uploadGroupProfilePicture: () -> GroupProfilePictureUploadResult?,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncTask<GroupProfilePictureUploadResult?, GroupProfilePictureUploadResult?>(multiDeviceManager),
    ActiveTask<ReflectionResult<GroupProfilePictureUploadResult?>> {
    override val type = "ReflectGroupSyncCreate"

    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<GroupProfilePictureUploadResult?> {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect group sync create of type {} when md is not active", type)
            return ReflectionResult.MultiDeviceNotActive()
        }

        return runTransaction(handle)
    }

    override val runPrecondition: () -> Boolean = {
        // Precondition: Group must not exist and members must exist as contacts
        groupModelData.notExists() && groupModelData.otherMembers.existAsContacts()
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> GroupProfilePictureUploadResult? = { handle ->
        val uploadResult = uploadGroupProfilePicture()
        if (uploadResult is GroupProfilePictureUploadResult.Success || uploadResult == null) {
            // Only reflect the group sync create if the upload was successful or null (no group profile picture set)
            reflectGroupSyncCreate(uploadResult, handle)
        }
        uploadResult
    }

    override val runAfterSuccessfulTransaction: (transactionResult: GroupProfilePictureUploadResult?) -> GroupProfilePictureUploadResult? = { it }

    private suspend fun reflectGroupSyncCreate(groupProfilePictureUploadSuccess: GroupProfilePictureUploadResult.Success?, handle: ActiveTaskCodec) {
        logger.info("Reflecting group sync create for group {}", groupModelData.name)
        val profilePictureChange = groupProfilePictureUploadSuccess?.let {
            ExpectedProfilePictureChange.Set.WithUpload(
                profilePictureUploadResultSuccess = groupProfilePictureUploadSuccess,
            )
        }

        val encryptedEnvelopeResult = getEncryptedGroupSyncCreate(
            groupModelData.toGroupSync(
                isPrivateChat = false,
                conversationVisibility = MdD2DSync.ConversationVisibility.NORMAL,
                expectedProfilePictureChange = profilePictureChange,
            ),
            multiDeviceManager.propertiesProvider.get(),
        )

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult,
            true,
            nonceFactory,
        )
    }

    private fun GroupModelData.notExists() =
        groupModelRepository.getByGroupIdentity(groupIdentity) == null

    private fun Iterable<String>.existAsContacts() =
        this.map { contactModelRepository.getByIdentity(it) }.all { it != null }
}
