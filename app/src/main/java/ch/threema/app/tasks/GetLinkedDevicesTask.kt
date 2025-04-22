/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.LinkedDevice
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.MessageFilterInstruction

private val logger = LoggingUtil.getThreemaLogger("GetLinkedDevicesTask")

/**
 *  Sends `GetDevicesInfo` to mediator and waits **indefinitely** for a response message of type `DevicesInfo`.
 *
 *  Filters out the own device from results and maps them to `LinkedDevice` models.
 */
class GetLinkedDevicesTask(serviceManager: ServiceManager) : ActiveTask<GetLinkedDevicesTask.LinkedDevicesResult> {
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    override val type: String = "GetLinkedDevicesTask"

    override suspend fun invoke(handle: ActiveTaskCodec): LinkedDevicesResult {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Aborting task because MD is not active anymore")
            return LinkedDevicesResult.Failure(IllegalStateException("MD is not active anymore"))
        }

        handle.write(OutboundD2mMessage.GetDevicesInfo())

        val devicesInfo: InboundD2mMessage.DevicesInfo = handle.read { inboundMessage ->
            when (inboundMessage) {
                is InboundD2mMessage.DevicesInfo -> MessageFilterInstruction.ACCEPT
                else -> MessageFilterInstruction.BYPASS_OR_BACKLOG
            }
        } as InboundD2mMessage.DevicesInfo

        val mdProperties: MultiDeviceProperties = try {
            multiDeviceManager.propertiesProvider.get()
        } catch (npe: NullPointerException) {
            return LinkedDevicesResult.Failure(npe)
        }

        return LinkedDevicesResult.Success(
            mapToModels(
                devicesInfo = devicesInfo,
                mediatorDeviceId = mdProperties.mediatorDeviceId,
                mdKeys = mdProperties.keys,
            ),
        )
    }

    /**
     * @param mediatorDeviceId Our own device id in the group
     */
    private fun mapToModels(
        devicesInfo: InboundD2mMessage.DevicesInfo,
        mediatorDeviceId: DeviceId,
        mdKeys: MultiDeviceKeys,
    ): List<LinkedDevice> =
        devicesInfo.augmentedDeviceInfo.mapNotNull { augmentedDeviceInfo: Map.Entry<DeviceId, InboundD2mMessage.DevicesInfo.AugmentedDeviceInfo> ->

            // Filter out own current device
            if (augmentedDeviceInfo.key == mediatorDeviceId) {
                return@mapNotNull null
            }

            val deviceInfo: D2dMessage.DeviceInfo = try {
                mdKeys.decryptDeviceInfo(augmentedDeviceInfo.value.encryptedDeviceInfo)
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

    /**
     * The result of getting the linked devices. Note that we primarily use this custom result due to a bug in mockk that can't properly handle
     * [kotlin.Result] types in coroutine verifications.
     */
    sealed interface LinkedDevicesResult {
        /**
         * Get the result in case it is successful, otherwise throws the failure.
         */
        fun getOrThrow(): List<LinkedDevice>

        /**
         * Get as a kotlin result.
         */
        fun toResult(): Result<List<LinkedDevice>>

        class Success(val linkedDevices: List<LinkedDevice>) : LinkedDevicesResult {
            override fun getOrThrow(): List<LinkedDevice> = linkedDevices
            override fun toResult() = Result.success(linkedDevices)
        }
        class Failure(val throwable: Throwable) : LinkedDevicesResult {
            override fun getOrThrow() = throw throwable
            override fun toResult() = Result.failure<List<LinkedDevice>>(throwable)
        }
    }
}
