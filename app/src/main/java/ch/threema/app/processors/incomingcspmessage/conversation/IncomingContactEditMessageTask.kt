package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.runCommonEditMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.AbstractMessageModel

private val logger = getThreemaLogger("IncomingContactEditMessageTask")

class IncomingContactEditMessageTask(
    editMessage: EditMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<EditMessage>(editMessage, triggerSource, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        return applyEdit()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        return applyEdit()
    }

    private fun applyEdit(): ReceiveStepsResult {
        logger.debug("IncomingContactEditMessageTask id: {}", message.data.messageId)

        val contactModel = contactService.getByIdentity(message.fromIdentity)
        if (contactModel == null) {
            logger.warn("Incoming Edit Message: No contact found for ${message.fromIdentity}")
            return ReceiveStepsResult.DISCARD
        }

        val fromReceiver: ContactMessageReceiver = contactService.createReceiver(contactModel)
        val editedMessage: AbstractMessageModel =
            runCommonEditMessageReceiveSteps(message, fromReceiver, messageService)
                ?: return ReceiveStepsResult.DISCARD

        messageService.saveEditedMessageText(editedMessage, message.data.text, message.date)

        return ReceiveStepsResult.SUCCESS
    }
}
