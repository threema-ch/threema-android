package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

private val logger = getThreemaLogger("ReflectedOutgoingContactSetProfilePictureTask")

internal class ReflectedOutgoingContactSetProfilePictureTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<SetProfilePictureMessage>(
    outgoingMessage = message,
    message = SetProfilePictureMessage.fromReflected(message),
    type = CspE2eMessageType.CONTACT_SET_PROFILE_PICTURE,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        val identity = messageReceiver.contact.identity
        contactModelRepository.getByIdentity(identity)
            ?.setProfilePictureBlobId(message.blobId)
            ?: logger.error("Received reflected outgoing message to unknown contact {}", identity)
    }
}
