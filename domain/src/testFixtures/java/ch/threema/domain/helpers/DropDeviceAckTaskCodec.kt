package ch.threema.domain.helpers

import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * This task codec answers each outbound [OutboundD2mMessage.DropDevice] with the corresponding [InboundD2mMessage.DropDeviceAck].
 */
open class DropDeviceAckTaskCodec : TransactionAckTaskCodec() {
    val droppedDevices: MutableList<DeviceId> = mutableListOf()

    override suspend fun write(message: OutboundMessage) {
        if (message is OutboundD2mMessage.DropDevice) {
            droppedDevices.add(message.deviceId)
            inboundMessages.add(InboundD2mMessage.DropDeviceAck(message.deviceId))
        } else {
            super.write(message)
        }
    }
}
