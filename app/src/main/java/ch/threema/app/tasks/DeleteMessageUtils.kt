package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.storage.models.AbstractMessageModel

private val logger = getThreemaLogger("DeleteMessageUtils")

fun runCommonDeleteMessageReceiveSteps(
    deleteMessage: DeleteMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    return runCommonDeleteMessageReceiveSteps(
        deleteMessage,
        deleteMessage.data.messageId,
        receiver,
        messageService,
    )
}

fun runCommonDeleteMessageReceiveSteps(
    deleteMessage: GroupDeleteMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    return runCommonDeleteMessageReceiveSteps(
        deleteMessage,
        deleteMessage.data.messageId,
        receiver,
        messageService,
    )
}

private fun runCommonDeleteMessageReceiveSteps(
    deleteMessage: AbstractMessage,
    messageId: Long,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    // Lookup the message with `message_id` originally sent by the sender within
    //  the associated conversation and let `message` be the result.
    val apiMessageId = MessageId(messageId).toString()
    val message = messageService.getMessageModelByApiMessageIdAndReceiver(apiMessageId, receiver)

    // 2. If `message` is not defined  or ... , discard the message and abort these steps.
    if (message == null) {
        logger.warn("Delete Message: No message found for id: {}", apiMessageId)
        return null
    }
    // 2. If `message` is not ... or the sender is not the original sender of `message`, discard the message and abort these steps.
    // Note: We only perform this check if the message is inbox
    if (!message.isOutbox && deleteMessage.fromIdentity != message.identity) {
        logger.warn(
            "Delete Message: original message's sender {} does not equal deleted message's sender {}",
            message.identity,
            deleteMessage.fromIdentity,
        )
        return null
    }

    // 3. If the `message` is not deletable because of its type, discard the message and abort these
    //    steps.
    if (!MessageUtil.doesMessageTypeAllowRemoteDeletion(message.type)) {
        logger.warn("Delete Message: Message of type {} cannot be deleted", message.type)
        return null
    }

    // Replace `message` with a message informing the user that the message of
    //  the sender has been removed at `created-at`.
    message.deletedAt = deleteMessage.date

    return message
}
