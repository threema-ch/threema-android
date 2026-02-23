package ch.threema.app.processors.incomingcspmessage.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingDeliveryReceiptTask")

class IncomingDeliveryReceiptTask(
    message: DeliveryReceiptMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<DeliveryReceiptMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processIncomingDeliveryReceipt()

    override suspend fun executeMessageStepsFromSync() = processIncomingDeliveryReceipt()

    private fun processIncomingDeliveryReceipt(): ReceiveStepsResult {
        val state = MessageUtil.receiptTypeToMessageState(message.receiptType)

        if (state == null) {
            logger.warn(
                "Message {} error: unknown delivery receipt type: {}",
                message.messageId,
                message.receiptType,
            )
            return ReceiveStepsResult.DISCARD
        }

        message.receiptMessageIds.forEach {
            logger.info(
                "Processing message {}: delivery receipt for {} (state = {})",
                message.messageId,
                it,
                state,
            )
        }

        message.receiptMessageIds.mapNotNull { receiptMessageId ->
            messageService.getContactMessageModel(receiptMessageId, message.fromIdentity)
        }.forEach { messageModel ->
            if (MessageUtil.isReaction(state)) {
                messageService.addMessageReaction(
                    messageModel,
                    state,
                    message.fromIdentity,
                    message.date,
                )
            } else {
                messageService.updateOutgoingMessageState(
                    messageModel,
                    state,
                    message.date,
                )
            }
        }
        return ReceiveStepsResult.SUCCESS
    }
}
