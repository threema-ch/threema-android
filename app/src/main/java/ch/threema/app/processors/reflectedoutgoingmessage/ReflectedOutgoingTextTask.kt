package ch.threema.app.processors.reflectedoutgoingmessage

import androidx.core.util.component1
import androidx.core.util.component2
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.QuoteUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType

private val logger = getThreemaLogger("ReflectedOutgoingTextTask")

internal class ReflectedOutgoingTextTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<TextMessage>(
    outgoingMessage = outgoingMessage,
    message = TextMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.TEXT,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override fun processOutgoingMessage() {
        messageService.getMessageModelByApiMessageIdAndReceiver(message.messageId.toString(), messageReceiver)?.run {
            // It is possible that a message gets reflected twice when the reflecting task gets restarted.
            logger.info("Skipping message {} as it already exists.", message.messageId)
            return
        }

        val messageModel: MessageModel = createMessageModel(
            MessageType.TEXT,
            MessageContentsType.TEXT,
        )
        val (body, messageId) = QuoteUtil.getBodyAndQuotedMessageId(message.text)
        messageModel.body = body
        messageModel.quotedMessageId = messageId
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { it.onNew(messageModel) }
    }
}
