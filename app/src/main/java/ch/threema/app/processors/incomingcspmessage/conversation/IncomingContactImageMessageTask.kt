package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.ImageMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingContactImageMessageTask(
    imageMessage: ImageMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<ImageMessage>(
    imageMessage,
    triggerSource,
    serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processIncomingMessage()

    override suspend fun executeMessageStepsFromSync() = processIncomingMessage()

    private fun processIncomingMessage(): ReceiveStepsResult =
        if (messageService.processIncomingContactMessage(message, triggerSource)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
