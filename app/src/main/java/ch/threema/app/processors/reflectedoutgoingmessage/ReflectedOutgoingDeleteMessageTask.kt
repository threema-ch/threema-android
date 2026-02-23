package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.runCommonDeleteMessageReceiveSteps
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

internal class ReflectedOutgoingDeleteMessageTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeleteMessage>(
    outgoingMessage = outgoingMessage,
    message = DeleteMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.DELETE_MESSAGE,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    override fun processOutgoingMessage() {
        runCommonDeleteMessageReceiveSteps(
            deleteMessage = message,
            receiver = messageReceiver,
            messageService = messageService,
        )?.let { validatedMessageModelToDelete ->
            messageService.deleteMessageContentsAndRelatedData(
                validatedMessageModelToDelete,
                message.date,
            )
        }
    }
}
