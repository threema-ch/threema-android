package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel
import ch.threema.storage.models.data.MessageContentsType

private val logger = getThreemaLogger("ReflectedOutgoingLocationTask")

internal class ReflectedOutgoingLocationTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<LocationMessage>(
    outgoingMessage = outgoingMessage,
    message = LocationMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.LOCATION,
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
            MessageType.LOCATION,
            MessageContentsType.LOCATION,
        )
        messageModel.locationData = LocationDataModel(
            latitude = message.latitude,
            longitude = message.longitude,
            accuracy = message.accuracy,
            poi = message.poi,
        )
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { it.onNew(messageModel) }
    }
}
