package ch.threema.app.multidevice.unlinking

import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo

sealed interface DropDeviceResult {
    /**
     *  @param remainingLinkedDevices A list containing all remaining linked devices in the current device group **excluding** our own.
     */
    data class Success(
        val remainingLinkedDevices: Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>,
    ) : DropDeviceResult

    sealed interface Failure : DropDeviceResult {
        data object Internal : Failure

        data object Timeout : Failure
    }
}
