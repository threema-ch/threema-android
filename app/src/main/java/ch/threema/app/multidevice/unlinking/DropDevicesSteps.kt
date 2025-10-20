/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.multidevice.unlinking

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.IS_FS_SUPPORTED_WITH_MD
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.d2m.socket.D2mCloseCode
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.ConnectionStoppedException
import ch.threema.domain.taskmanager.MessageFilterInstruction
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D.TransactionScope

private val logger = LoggingUtil.getThreemaLogger("DropDevicesSteps")

/**
 * The intent for running the drop devices steps.
 */
sealed interface DropDevicesIntent {

    val deviceIdsToDrop: Set<DeviceId>?

    /**
     * Drop one or more devices from the device group. Note that this must not be used to drop this device.
     *
     * @throws IllegalArgumentException if [deviceIdsToDrop] is empty or contains this device's id
     */
    class DropDevices(override val deviceIdsToDrop: Set<DeviceId>, thisDeviceId: DeviceId) : DropDevicesIntent {
        init {
            require(deviceIdsToDrop.isNotEmpty())
            require(!deviceIdsToDrop.contains(thisDeviceId))
        }
    }

    /**
     * Deactivate multi device. This will drop all other devices in the device group.
     */
    data object Deactivate : DropDevicesIntent {
        override val deviceIdsToDrop: Set<DeviceId>? = null
    }

    /**
     * Deactivate multi device if there are no other devices. This doesn't drop any devices if there are other devices in the device group.
     */
    data object DeactivateIfAlone : DropDevicesIntent {
        override val deviceIdsToDrop: Set<DeviceId> = emptySet()
    }
}

suspend fun runDropDevicesSteps(
    intent: DropDevicesIntent,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
): DropDeviceResult {
    logger.info(
        "Running drop devices steps with the intent to {}",
        when (intent) {
            is DropDevicesIntent.DropDevices -> "drop other devices"
            is DropDevicesIntent.Deactivate -> "deactivate multi device"
            is DropDevicesIntent.DeactivateIfAlone -> "deactivate multi device if alone"
        },
    )

    val multiDeviceManager = serviceManager.multiDeviceManager

    if (!multiDeviceManager.isMultiDeviceActive) {
        logger.warn("Multi device is currently not active")
        multiDeviceManager.enableForwardSecurity(serviceManager)
        return DropDeviceResult.Failure.Internal
    }

    val remainingDevices = dropDevices(
        deviceIdsToDrop = intent.deviceIdsToDrop,
        serviceManager = serviceManager,
        handle = handle,
    )

    return DropDeviceResult.Success(remainingDevices)
}

private suspend fun dropDevices(
    deviceIdsToDrop: Set<DeviceId>?,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
): Map<DeviceId, DevicesInfo.AugmentedDeviceInfo> {
    val multiDeviceManager = serviceManager.multiDeviceManager
    val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

    var remainingDevices: Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>? = null

    try {
        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = TransactionScope.Scope.DROP_DEVICE,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            val thisDeviceId = multiDeviceProperties.mediatorDeviceId

            val otherDevices = handle.getOtherDevices(thisDeviceId)
            // Only drop devices that are currently in the device group. If no specific devices should be dropped, drop all other devices.
            val sanitizedDevicesToDrop = deviceIdsToDrop?.intersect(otherDevices.keys) ?: otherDevices.keys
            sanitizedDevicesToDrop
                .onEach { deviceId -> logger.info("Dropping device {}", deviceId) }
                .onEach { deviceId -> handle.sendDropDevice(deviceId) }
                .onEach { deviceId -> handle.awaitDeviceDropAck(deviceId) }
                .forEach { deviceId -> logger.info("Dropped device {}", deviceId) }

            if (sanitizedDevicesToDrop == otherDevices.keys) {
                dropThisDevice(thisDeviceId, handle)

                // Note that it is important that the section between here and where the remaining devices are set is not suspendable. Otherwise these
                // steps will be canceled and started again (depending on the behavior of the running task) - which is not necessary.

                multiDeviceManager.removeMultiDeviceLocally(serviceManager)

                // Enable FS
                if (!IS_FS_SUPPORTED_WITH_MD) {
                    multiDeviceManager.enableForwardSecurity(serviceManager)
                }

                // Reconnect as we now need a csp connection instead of a d2m connection
                multiDeviceManager.reconnect()
            }

            remainingDevices = otherDevices.filterKeys { deviceId -> !sanitizedDevicesToDrop.contains(deviceId) }

            // The transaction will be committed here. If the own device has been dropped and MD is disabled now, the connection is closed and
            // therefore we can expect a NetworkException as committing the transaction would require a connection. However, it is not necessary to
            // commit the transaction in this case, therefore we can safely catch the exception and verify based on the nullability of
            // `remainingDevices` whether we have achieved what we wanted to.
        }
    } catch (networkException: NetworkException) {
        remainingDevices.let { otherDevices ->
            if (otherDevices == null) {
                // In case the remaining devices have not been set, a reconnection may have happened before the steps have performed their work.
                // Therefore we throw the exception so that the running task can run again or the user at least will get an error.
                logger.warn("Network error before all required devices have been dropped", networkException)
                throw networkException
            } else if (otherDevices.isNotEmpty()) {
                // In this case the transaction could not be completed because of a disconnect. Therefore, we need to throw the exception to trigger a
                // new execution of the task running these steps.
                logger.warn("Network error while committing the transaction", networkException)
                throw networkException
            } else {
                // This is the default path when this device has been dropped. This is due to the attempt to send the transaction commit message after
                // this device has been dropped. We can catch this network exception and successfully return the other devices.
                logger.info("Network exception after this device has been deleted")
                return otherDevices
            }
        }
    }
    remainingDevices.let { otherDevices ->
        if (otherDevices != null) {
            // This is the path if one or more other devices have been dropped and this device wasn't dropped (because there is at least one other
            // device in the device group).
            logger.info("The device(s) have been dropped and the transaction has been completed successfully.")
            return otherDevices
        } else {
            // This can never happen.
            error("Drop devices steps error")
        }
    }
}

private suspend fun dropThisDevice(thisDeviceId: DeviceId, handle: ActiveTaskCodec) {
    logger.info("Dropping this device")
    handle.sendDropDevice(thisDeviceId)
    // When dropping this device, we will get an ack but the connection might be closed while awaiting it. Therefore we check the close
    // code.
    try {
        handle.awaitDeviceDropAck(thisDeviceId)
        logger.info("Received drop device ack for this device")
    } catch (connectionStoppedException: ConnectionStoppedException) {
        if (connectionStoppedException.closeReason is D2mSocketCloseReason &&
            (connectionStoppedException.closeReason as D2mSocketCloseReason).closeCode.code == D2mCloseCode.D2M_DEVICE_DROPPED
        ) {
            logger.info("Connection has been closed with close code {}", D2mCloseCode.D2M_DEVICE_DROPPED)
        } else {
            logger.warn("Connection has been closed unexpectedly", connectionStoppedException)
            throw connectionStoppedException
        }
    }
    logger.info("Dropped this device")
}

private suspend fun ActiveTaskCodec.getOtherDevices(thisDeviceId: DeviceId): Map<DeviceId, DevicesInfo.AugmentedDeviceInfo> {
    write(OutboundD2mMessage.GetDevicesInfo())
    val devicesInfo = read { inboundMessage ->
        when (inboundMessage) {
            is DevicesInfo -> MessageFilterInstruction.ACCEPT
            else -> MessageFilterInstruction.BYPASS_OR_BACKLOG
        }
    } as DevicesInfo

    return devicesInfo.augmentedDeviceInfo.filterKeys { deviceId -> deviceId != thisDeviceId }
}

private suspend fun ActiveTaskCodec.sendDropDevice(deviceId: DeviceId) {
    write(OutboundD2mMessage.DropDevice(deviceId))
}

private suspend fun ActiveTaskCodec.awaitDeviceDropAck(deviceId: DeviceId) {
    read { inboundMessage ->
        if (inboundMessage is InboundD2mMessage.DropDeviceAck && inboundMessage.deviceId == deviceId) {
            MessageFilterInstruction.ACCEPT
        } else {
            MessageFilterInstruction.BYPASS_OR_BACKLOG
        }
    }
}
