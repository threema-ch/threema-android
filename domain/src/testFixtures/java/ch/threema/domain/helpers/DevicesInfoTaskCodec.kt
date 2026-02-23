package ch.threema.domain.helpers

import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * This task codec answers each outgoing [OutboundD2mMessage.GetDevicesInfo] with a response given in [deviceInfoResponses] in the same order of the
 * list.
 * @throws NoSuchElementException if the number of [OutboundD2mMessage.GetDevicesInfo] messages that are sent exceeds the number of responses in
 * [deviceInfoResponses]
 */
open class DevicesInfoTaskCodec(
    private val deviceInfoResponses: MutableList<InboundD2mMessage.DevicesInfo>,
) : DropDeviceAckTaskCodec() {
    override suspend fun write(message: OutboundMessage) {
        if (message is OutboundD2mMessage.GetDevicesInfo) {
            inboundMessages.add(deviceInfoResponses.removeAt(0))
        } else {
            super.write(message)
        }
    }
}
