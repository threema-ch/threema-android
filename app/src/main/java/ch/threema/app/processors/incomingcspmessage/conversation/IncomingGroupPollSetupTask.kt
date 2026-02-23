package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingGroupPollSetupTask(
    private val groupPollSetupMessage: GroupPollSetupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        if (runCommonGroupReceiveSteps(groupPollSetupMessage, handle, serviceManager) == null) {
            return ReceiveStepsResult.DISCARD
        }
        return processPollSetupMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processPollSetupMessage()

    private fun processPollSetupMessage(): ReceiveStepsResult {
        val successfullyProcessed =
            messageService.processIncomingGroupMessage(groupPollSetupMessage, triggerSource)
        return if (successfullyProcessed) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
