package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.profilepicture.ProfilePicture
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.types.Identity

class OutgoingGroupSetProfilePictureTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    override val recipientIdentities: Set<Identity>,
    private val profilePicture: ProfilePicture,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    private val groupProfilePictureUploadSuccess by lazy {
        when (val result = serviceManager.groupProfilePictureUploader.tryUploadingGroupProfilePicture(profilePicture)) {
            is GroupProfilePictureUploadResult.Success -> result
            is GroupProfilePictureUploadResult.Failure -> throw ProtocolException("Could not upload group profile picture")
        }
    }

    override val type = "OutgoingGroupSetProfilePictureTask"

    override val messageId = messageId ?: MessageId.random()

    override fun createGroupMessage(): AbstractGroupMessage {
        return GroupSetProfilePictureMessage()
            .also {
                it.blobId = groupProfilePictureUploadSuccess.blobId
                it.encryptionKey = groupProfilePictureUploadSuccess.encryptionKey
                it.size = groupProfilePictureUploadSuccess.size
            }
    }

    override fun serialize() = null
}
