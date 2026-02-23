package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingContactLocationMessageTask(
    private val locationMessage: LocationMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<LocationMessage>(locationMessage, triggerSource, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processLocationMessage()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processLocationMessage()

    private fun processLocationMessage(): ReceiveStepsResult {
        val processedMessageSuccessfully = messageService.processIncomingContactMessage(locationMessage, triggerSource)
        return if (processedMessageSuccessfully) ReceiveStepsResult.SUCCESS else ReceiveStepsResult.DISCARD
    }
}
