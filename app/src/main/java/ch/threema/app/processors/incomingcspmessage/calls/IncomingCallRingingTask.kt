package ch.threema.app.processors.incomingcspmessage.calls

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingCallRingingTask(
    message: VoipCallRingingMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<VoipCallRingingMessage>(message, triggerSource, serviceManager) {
    private val voipStateService = serviceManager.voipStateService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processCallRinging()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = processCallRinging()

    private fun processCallRinging() =
        if (voipStateService.handleCallRinging(message)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
