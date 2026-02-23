package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("SendProfilePictureTask")

/**
 * This task sends out either a set-profile-picture message or a delete-profile-picture message to
 * the given contact. Note that this task does not check when the profile picture has been sent the
 * last time.
 */
class SendProfilePictureTask(private val toIdentity: Identity, serviceManager: ServiceManager) :
    OutgoingProfilePictureTask(serviceManager) {
    override val type: String = "SendProfilePictureTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val data = userService.uploadUserProfilePictureOrGetPreviousUploadData()
        if (data.blobId == null) {
            logger.warn("Blob ID is null; cannot send profile picture")
            return
        }

        return if (data.blobId.contentEquals(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)) {
            sendDeleteProfilePictureMessage(toIdentity, handle)
        } else {
            sendSetProfilePictureMessage(data, toIdentity, handle)
        }
    }

    override fun serialize(): SerializableTaskData = SendProfilePictureData(toIdentity)

    @Serializable
    data class SendProfilePictureData(private val toIdentity: Identity) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            SendProfilePictureTask(toIdentity, serviceManager)
    }
}
