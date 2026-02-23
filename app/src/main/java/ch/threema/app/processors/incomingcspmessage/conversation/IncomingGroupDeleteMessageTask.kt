package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.tasks.runCommonDeleteMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingGroupDeleteMessageTask")

class IncomingGroupDeleteMessageTask(
    message: GroupDeleteMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupDeleteMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService
    private val groupService = serviceManager.groupService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        if (runCommonGroupReceiveSteps(message, handle, serviceManager) == null) {
            logger.info("Could not find group for delete message")
            return ReceiveStepsResult.DISCARD
        }

        return processGroupDeleteMessage()
    }

    override suspend fun executeMessageStepsFromSync() = processGroupDeleteMessage()

    private fun processGroupDeleteMessage(): ReceiveStepsResult {
        logger.debug("IncomingGroupDeleteMessageTask id: {}", message.data.messageId)

        val groupModel =
            groupService.getByGroupMessage(message) ?: return ReceiveStepsResult.DISCARD

        val receiver = groupService.createReceiver(groupModel)
        val messageModel = runCommonDeleteMessageReceiveSteps(message, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD

        messageService.deleteMessageContentsAndRelatedData(messageModel, message.date)

        return ReceiveStepsResult.SUCCESS
    }
}
