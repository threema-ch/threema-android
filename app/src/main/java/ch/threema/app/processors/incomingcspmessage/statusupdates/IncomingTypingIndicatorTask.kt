package ch.threema.app.processors.incomingcspmessage.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingTypingIndicatorTask(
    message: TypingIndicatorMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<TypingIndicatorMessage>(message, triggerSource, serviceManager) {
    private val contactService = serviceManager.contactService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processIncomingTypingIndicator()

    override suspend fun executeMessageStepsFromSync() = processIncomingTypingIndicator()

    private fun processIncomingTypingIndicator(): ReceiveStepsResult {
        if (contactService.getByIdentity(message.fromIdentity) != null) {
            contactService.setIsTyping(message.fromIdentity, message.isTyping)
            return ReceiveStepsResult.SUCCESS
        }
        return ReceiveStepsResult.DISCARD
    }
}
