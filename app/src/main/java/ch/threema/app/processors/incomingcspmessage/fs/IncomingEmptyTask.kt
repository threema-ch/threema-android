package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.EmptyMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingEmptyTask")

class IncomingEmptyTask(
    emptyMessage: EmptyMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<EmptyMessage>(emptyMessage, triggerSource, serviceManager) {
    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.info("Processed incoming empty message {}", message.messageId)
        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.warn("Received empty message from sync with message id {}", message.messageId)
        return ReceiveStepsResult.DISCARD
    }
}
