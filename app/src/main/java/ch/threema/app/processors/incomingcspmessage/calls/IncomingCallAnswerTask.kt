package ch.threema.app.processors.incomingcspmessage.calls

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingCallAnswerTask(
    message: VoipCallAnswerMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<VoipCallAnswerMessage>(message, triggerSource, serviceManager) {
    private val voipStateService = serviceManager.voipStateService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processCallAnswer()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = processCallAnswer()

    private fun processCallAnswer() =
        if (voipStateService.handleCallAnswer(message)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
