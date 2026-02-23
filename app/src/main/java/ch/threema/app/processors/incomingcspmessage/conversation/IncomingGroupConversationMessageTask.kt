package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingGroupConversationMessageTask(
    message: AbstractGroupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<AbstractGroupMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        if (runCommonGroupReceiveSteps(message, handle, serviceManager) == null) {
            return ReceiveStepsResult.DISCARD
        }

        return if (messageService.processIncomingGroupMessage(message, triggerSource)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        return if (messageService.processIncomingGroupMessage(message, triggerSource)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
