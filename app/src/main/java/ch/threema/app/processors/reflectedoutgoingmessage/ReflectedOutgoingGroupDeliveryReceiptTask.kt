package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.group.GroupMessageModel
import java.util.Date

private val logger = getThreemaLogger("ReflectedOutgoingGroupDeliveryReceiptTask")

internal class ReflectedOutgoingGroupDeliveryReceiptTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<GroupDeliveryReceiptMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupDeliveryReceiptMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.GROUP_DELIVERY_RECEIPT,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val myIdentity by lazy { serviceManager.identityStore.getIdentityString()!! }

    override fun processOutgoingMessage() {
        logger.info(
            "Processing message {}: reflected outgoing group delivery receipt",
            outgoingMessage.messageId,
        )

        val messageState: MessageState? =
            MessageUtil.receiptTypeToMessageState(message.receiptType)
        if (messageState == null || !MessageUtil.isReaction(messageState)) {
            logger.warn(
                "Message {} error: unknown or unsupported delivery receipt type: {}",
                message.messageId,
                message.receiptType,
            )
            return
        }

        for (receiptMessageId: MessageId in message.receiptMessageIds) {
            logger.info(
                "Processing message {}: group delivery receipt for {} (state = {})",
                outgoingMessage.messageId,
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
                    "Group message model ({}) for reflected outgoing group delivery receipt is null",
                    receiptMessageId,
                )
                continue
            }
            messageService.addMessageReaction(
                groupMessageModel,
                messageState,
                // the identity that reacted (this is us => reflected outgoing message)
                myIdentity,
                Date(outgoingMessage.createdAt),
            )
        }
    }
}
