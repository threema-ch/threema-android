/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = LoggingUtil.getThreemaLogger("DeleteDeviceGroupTask")

class DeleteDeviceGroupTask(
    serviceManager: ServiceManager
) : ActiveTask<Unit> {
    override val type: String = "DeleteDeviceGroupTask"

    private val multiDeviceManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val properties: MultiDeviceProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        // 1. Abort if md is not active
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Abort md device group deletion: md is not active")
            return
        }

        // 2. Drop _all_ other devices from group
        var linkedDevicesIds = getLinkedDevicesIds(handle)
        while (linkedDevicesIds.isNotEmpty()) {
            // Remove in a loop as _theoretically_ other devices could be linked in the meantime.
            // Normally the loop should only be run once.
            logger.debug("Drop {} linked device(s)", linkedDevicesIds.size)
            linkedDevicesIds.forEach {
                DropDeviceTask(it).invoke(handle)
            }
            linkedDevicesIds = getLinkedDevicesIds(handle)
        }

        // 3. Drop own device from group
        logger.debug("Drop own device")
        DropDeviceTask(properties.mediatorDeviceId).invoke(handle)
    }

    /**
     * @return The [DeviceId]s of all linked devices _excluding_ the own device.
     */
    private suspend fun getLinkedDevicesIds(handle: ActiveTaskCodec): Set<DeviceId> {
        return GetDevicesInfoTask().invoke(handle).augmentedDeviceInfo.keys
            .filter { it != properties.mediatorDeviceId }
            .toSet()
    }
}
