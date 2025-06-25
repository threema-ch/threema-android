/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.multidevice

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2dMessage.DeviceInfo.Platform
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.DeviceSlotExpirationPolicy
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys

private val logger = LoggingUtil.getThreemaLogger("LinkedDevice")

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
