package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.runCommonEditMessageReceiveSteps
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.models.AbstractMessageModel

internal class ReflectedOutgoingEditMessageTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<EditMessage>(
    outgoingMessage = outgoingMessage,
    message = EditMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.EDIT_MESSAGE,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override fun processOutgoingMessage() {
        runCommonEditMessageReceiveSteps(
            editMessage = message,
            receiver = messageReceiver,
            messageService = messageService,
        )?.let { validEditMessageModel: AbstractMessageModel ->
            messageService.saveEditedMessageText(
                validEditMessageModel,
                message.data.text,
                message.date,
            )
        }
    }
}
