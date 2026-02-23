package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage

private val logger = getThreemaLogger("ReflectedOutgoingContactRequestProfilePictureTask")

/**
 * Note that currently outgoing contact request profile picture messages are not reflected.
 * Therefore this task is currently never executed.
 */
internal class ReflectedOutgoingContactRequestProfilePictureTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<ContactRequestProfilePictureMessage>(
    outgoingMessage = outgoingMessage,
    message = ContactRequestProfilePictureMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.CONTACT_REQUEST_PROFILE_PICTURE,
    serviceManager = serviceManager,
) {

    override fun processOutgoingMessage() {
        val identity = messageReceiver.contact.identity
        contactModelRepository.getByIdentity(identity)
            ?.setIsRestored(false)
            ?: logger.error("Received outgoing message for unknown contact {}", identity)
    }
}
