package ch.threema.app.processors.incomingcspmessage.calls

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingCallHangupTask(
    message: VoipCallHangupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<VoipCallHangupMessage>(message, triggerSource, serviceManager) {
    private val voipStateService = serviceManager.voipStateService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processCallHangup()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = processCallHangup()

    private fun processCallHangup() =
        if (voipStateService.handleRemoteCallHangup(message)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
