package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage
import ch.threema.storage.models.ContactModel

private val logger = getThreemaLogger("ReflectedOutgoingDeleteProfilePictureTask")

internal class ReflectedOutgoingDeleteProfilePictureTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeleteProfilePictureMessage>(
    outgoingMessage = outgoingMessage,
    message = DeleteProfilePictureMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.CONTACT_DELETE_PROFILE_PICTURE,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        val identity = messageReceiver.contact.identity
        contactModelRepository.getByIdentity(identity)
            ?.setProfilePictureBlobId(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)
            ?: logger.error("Received reflected outgoing message for unknown contact {}", identity)
    }
}
