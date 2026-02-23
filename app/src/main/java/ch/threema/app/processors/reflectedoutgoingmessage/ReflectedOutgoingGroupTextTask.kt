package ch.threema.app.processors.reflectedoutgoingmessage

import androidx.core.util.component1
import androidx.core.util.component2
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.QuoteUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType

private val logger = getThreemaLogger("ReflectedOutgoingGroupTextTask")

internal class ReflectedOutgoingGroupTextTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupTextMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupTextMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.GROUP_TEXT,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override fun processOutgoingMessage() {
        check(outgoingMessage.conversation.hasGroup()) {
            "The message does not have a group identity set"
        }

        messageService.getMessageModelByApiMessageIdAndReceiver(message.messageId.toString(), messageReceiver)?.run {
            // It is possible that a message gets reflected twice when the reflecting task gets restarted.
            logger.info("Skipping message {} as it already exists.", message.messageId)
            return
        }

        val messageModel = createMessageModel(
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
