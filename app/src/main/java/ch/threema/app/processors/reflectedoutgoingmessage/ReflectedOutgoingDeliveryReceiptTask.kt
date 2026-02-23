package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = getThreemaLogger("ReflectedOutgoingDeliveryReceiptTask")

internal class ReflectedOutgoingDeliveryReceiptTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeliveryReceiptMessage>(
    outgoingMessage = outgoingMessage,
    message = DeliveryReceiptMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.DELIVERY_RECEIPT,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val notificationService by lazy { serviceManager.notificationService }
    private val myIdentity by lazy { serviceManager.identityStore.getIdentity()!! }

    override fun processOutgoingMessage() {
        logger.info("Processing reflected outgoing delivery receipt")

        val deliveryReceiptMessage = DeliveryReceiptMessage.fromReflected(outgoingMessage)
        val state = MessageUtil.receiptTypeToMessageState(deliveryReceiptMessage.receiptType)

        if (state == null) {
            logger.warn("Message {} error: unknown delivery receipt type", outgoingMessage.messageId)
            return
        }

        val identity = outgoingMessage.conversation.contact

        for (messageId in deliveryReceiptMessage.receiptMessageIds) {
            val messageModel = messageService.getContactMessageModel(messageId, identity)
            if (messageModel == null) {
                logger.warn(
                    "Message model ({}) for reflected outgoing delivery receipt is null",
                    messageId,
                )
                continue
            }

            updateMessage(messageModel, state)

            if (state == MessageState.READ) {
                notificationService.cancel(messageReceiver)
            }
        }
    }

    private fun updateMessage(messageModel: AbstractMessageModel, state: MessageState) {
        if (MessageUtil.isReaction(state)) {
            messageService.addMessageReaction(
                messageModel,
                state,
                // the identity that reacted (this is us => reflected outgoing message)
                myIdentity,
                Date(outgoingMessage.createdAt),
            )
        } else {
            when (state) {
                MessageState.DELIVERED -> {
                    val date = Date(outgoingMessage.createdAt)
                    // The delivered at date is stored in created at for incoming messages
                    messageModel.createdAt = date
                    messageModel.modifiedAt = date
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                MessageState.READ -> {
                    val date = Date(outgoingMessage.createdAt)
                    messageModel.readAt = date
                    messageModel.modifiedAt = date
                    messageModel.isRead = true
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                else -> logger.error("Unsupported delivery receipt reflected of state {}", state)
            }
        }
    }
}
