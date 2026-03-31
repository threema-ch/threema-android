package ch.threema.app.processors.incomingcspmessage.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.group.GroupMessageModel

private val logger = getThreemaLogger("IncomingGroupDeliveryReceiptTask")

class IncomingGroupDeliveryReceiptTask(
    message: GroupDeliveryReceiptMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupDeliveryReceiptMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        executeMessageSteps(
            runCommonGroupReceiveSteps = {
                runCommonGroupReceiveSteps(message, handle, serviceManager)
            },
        )

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = executeMessageSteps()

    private suspend fun executeMessageSteps(
        runCommonGroupReceiveSteps: (suspend () -> GroupModel?)? = null,
    ): ReceiveStepsResult {
        logger.info("Processing message {}: incoming group delivery receipt", message.messageId)

        val messageState: MessageState? = MessageUtil.receiptTypeToMessageState(message.receiptType)
        if (messageState == null || !MessageUtil.isReaction(messageState)) {
            logger.warn(
                "Message {} error: unknown or unsupported delivery receipt type: {}",
                message.messageId,
                message.receiptType,
            )
            return ReceiveStepsResult.DISCARD
        }

        if (runCommonGroupReceiveSteps != null) {
            // If the common group receive steps did not succeed (null is returned), ignore this delivery receipt
            runCommonGroupReceiveSteps() ?: return ReceiveStepsResult.DISCARD
        }

        for (receiptMessageId: MessageId in message.receiptMessageIds) {
            logger.info(
                "Processing message {}: group delivery receipt for {} (state = {})",
                message.messageId,
                receiptMessageId,
                messageState,
            )
            val groupMessageModel: GroupMessageModel? = messageService.getGroupMessageModel(
                receiptMessageId,
                message.groupCreator,
                message.apiGroupId,
            )
            if (groupMessageModel == null) {
                logger.warn(
                    "Group message model ({}) for incoming group delivery receipt is null",
                    receiptMessageId,
                )
                continue
            }
            messageService.addMessageReaction(
                groupMessageModel,
                messageState,
                message.fromIdentity,
                message.date,
            )
        }

        return ReceiveStepsResult.SUCCESS
    }
}
