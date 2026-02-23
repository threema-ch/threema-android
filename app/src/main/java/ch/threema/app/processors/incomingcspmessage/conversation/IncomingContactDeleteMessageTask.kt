package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.runCommonDeleteMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingContactDeleteMessageTask")

class IncomingContactDeleteMessageTask(
    message: DeleteMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<DeleteMessage>(message, triggerSource, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processContactDeleteMessage()

    override suspend fun executeMessageStepsFromSync() = processContactDeleteMessage()

    private fun processContactDeleteMessage(): ReceiveStepsResult {
        logger.debug("IncomingContactDeleteMessageTask id: {}", message.data.messageId)

        val contactModel = contactService.getByIdentity(message.fromIdentity)
        if (contactModel == null) {
            logger.warn("Incoming Delete Message: No contact found for {}", message.fromIdentity)
            return ReceiveStepsResult.DISCARD
        }

        val receiver = contactService.createReceiver(contactModel)
        val messageModel = runCommonDeleteMessageReceiveSteps(message, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD

        messageService.deleteMessageContentsAndRelatedData(messageModel, message.date)

        return ReceiveStepsResult.SUCCESS
    }
}
