package ch.threema.app.processors.incomingcspmessage.calls

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingCallOfferTask(
    message: VoipCallOfferMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<VoipCallOfferMessage>(message, triggerSource, serviceManager) {
    private val voipStateService = serviceManager.voipStateService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processCallOffer()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = processCallOffer()

    private fun processCallOffer(): ReceiveStepsResult =
        if (voipStateService.handleCallOffer(message)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
