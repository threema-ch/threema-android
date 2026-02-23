package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingGroupLocationMessageTask")

class IncomingGroupLocationMessageTask(
    private val groupLocationMessage: GroupLocationMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupLocationMessage>(
    groupLocationMessage,
    triggerSource,
    serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        runCommonGroupReceiveSteps(message, handle, serviceManager) ?: run {
            logger.warn("Discarding message ${message.messageId}: Could not find group for incoming group location message")
            return ReceiveStepsResult.DISCARD
        }
        return processGroupLocationMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processGroupLocationMessage()

    private fun processGroupLocationMessage(): ReceiveStepsResult {
        val processedMessageSuccessfully =
            messageService.processIncomingGroupMessage(groupLocationMessage, triggerSource)
        return if (processedMessageSuccessfully) ReceiveStepsResult.SUCCESS else ReceiveStepsResult.DISCARD
    }
}
