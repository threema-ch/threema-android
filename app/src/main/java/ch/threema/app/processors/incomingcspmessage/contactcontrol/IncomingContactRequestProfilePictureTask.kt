package ch.threema.app.processors.incomingcspmessage.contactcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingContactRequestProfilePictureTask")

class IncomingContactRequestProfilePictureTask(
    message: ContactRequestProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<ContactRequestProfilePictureMessage>(
    message,
    triggerSource,
    serviceManager,
) {
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        return processIncomingContactRequestProfilePictureMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        return processIncomingContactRequestProfilePictureMessage()
    }

    private fun processIncomingContactRequestProfilePictureMessage(): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(message.fromIdentity)
        if (contactModel == null) {
            logger.warn("Received incoming contact request profile picture message from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        contactModel.setProfilePictureBlobId(null)

        return ReceiveStepsResult.SUCCESS
    }
}
