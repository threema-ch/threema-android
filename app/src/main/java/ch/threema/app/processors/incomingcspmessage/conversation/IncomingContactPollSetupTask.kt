package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingContactPollSetupTask(
    private val pollSetupMessage: PollSetupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processPollSetupMessage()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processPollSetupMessage()

    private fun processPollSetupMessage(): ReceiveStepsResult {
        val successfullyProcessed = messageService.processIncomingContactMessage(pollSetupMessage, triggerSource)
        return if (successfullyProcessed) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
