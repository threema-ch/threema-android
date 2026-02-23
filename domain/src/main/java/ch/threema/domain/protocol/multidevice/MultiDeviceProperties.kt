package ch.threema.domain.protocol.multidevice

import androidx.annotation.AnyThread
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolVersion
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.DeviceSlotState
import ch.threema.domain.protocol.connection.data.InboundD2mMessage

data class MultiDeviceProperties(
    val registrationTime: ULong?,
    val mediatorDeviceId: DeviceId,
    val cspDeviceId: DeviceId,
    val keys: MultiDeviceKeys,
    val deviceInfo: D2dMessage.DeviceInfo,
    val protocolVersion: D2mProtocolVersion,
    val serverInfoListener: (serverInfo: InboundD2mMessage.ServerInfo) -> Unit,
) {
    val deviceSlotState: DeviceSlotState = if (registrationTime == null) {
        DeviceSlotState.NEW
    } else {
        DeviceSlotState.EXISTING
    }

    /**
     * Call this method when a [InboundD2mMessage.ServerInfo] is received to propagate the info to the
     * [serverInfoListener].
     */
    @AnyThread
    fun notifyServerInfo(serverInfo: InboundD2mMessage.ServerInfo) {
        serverInfoListener.invoke(serverInfo)
    }

    override fun toString() =
        "MultiDeviceProperties(registrationTime=$registrationTime, mediatorDeviceId=$mediatorDeviceId, " +
            "cspDeviceId=$cspDeviceId, keys=********, deviceInfo=$deviceInfo, protocolVersion=$protocolVersion, " +
            "serverInfoListener=$serverInfoListener, deviceSlotState=$deviceSlotState)"
}
