package ch.threema.app.multidevice

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2dMessage.DeviceInfo.Platform
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.DeviceSlotExpirationPolicy
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys

private val logger = getThreemaLogger("LinkedDevice")

data class LinkedDevice(
    val deviceId: DeviceId,
    val platform: Platform,
    val platformDetails: String,
    val appVersion: String,
    val label: String,
    val connectedSince: ULong?,
    val lastDisconnectAt: ULong?,
    val deviceSlotExpirationPolicy: DeviceSlotExpirationPolicy,
) {
    companion object {
        fun fromAugmentedDevicesInfo(
            augmentedDevicesInfo: Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>,
            multiDeviceKeys: MultiDeviceKeys,
        ): List<LinkedDevice> {
            return augmentedDevicesInfo.mapNotNull { augmentedDeviceInfo: Map.Entry<DeviceId, DevicesInfo.AugmentedDeviceInfo> ->
                val deviceInfo: D2dMessage.DeviceInfo = try {
                    multiDeviceKeys.decryptDeviceInfo(augmentedDeviceInfo.value.encryptedDeviceInfo)
                } catch (exception: Exception) {
                    logger.error("Could not decrypt linked device info", exception)
                    // TODO(ANDR-3576): Distinguish between models with an unknown platform
                    //  and models where the decryption of `encryptedDeviceInfo` failed.
                    D2dMessage.DeviceInfo.INVALID_DEVICE_INFO
                }

                LinkedDevice(
                    deviceId = augmentedDeviceInfo.key,
                    platform = deviceInfo.platform,
                    platformDetails = deviceInfo.platformDetails,
                    appVersion = deviceInfo.appVersion,
                    label = deviceInfo.label,
                    connectedSince = augmentedDeviceInfo.value.connectedSince,
                    lastDisconnectAt = augmentedDeviceInfo.value.lastDisconnectAt,
                    deviceSlotExpirationPolicy = augmentedDeviceInfo.value.deviceSlotExpirationPolicy,
                )
            }
        }
    }
}

fun Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>.toLinkedDevices(multiDeviceKeys: MultiDeviceKeys): List<LinkedDevice> =
    LinkedDevice.fromAugmentedDevicesInfo(
        augmentedDevicesInfo = this,
        multiDeviceKeys = multiDeviceKeys,
    )
