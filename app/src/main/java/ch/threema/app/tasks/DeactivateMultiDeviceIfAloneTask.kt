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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.LinkedDevice
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("DeactivateMultiDeviceIfAloneTask")

/**
 * This task should be run periodically to check whether this is the only device in the device group. If this is the case, this task will deactivate
 * multi device.
 */
class DeactivateMultiDeviceIfAloneTask(private val serviceManager: ServiceManager) : ActiveTask<Unit>, PersistableTask {
    override val type = "DeactivateMultiDeviceIfAloneTask"

    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.error("Multi device is already deactivated")
            return
        }

        logger.info("Checking multi device group size to determine if multi-device must be active anymore")
        val linkedDevices: List<LinkedDevice> = GetLinkedDevicesTask(serviceManager).invoke(handle).getOrThrow()
        if (linkedDevices.isEmpty()) {
            logger.info("Deactivate MD because we are the only device left in the device group")
            multiDeviceManager.deactivate(serviceManager, handle)
        }
    }

    @Serializable
    data object DeactivateMultiDeviceIfAloneTaskData : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager) = DeactivateMultiDeviceIfAloneTask(serviceManager)
    }

    override fun serialize() = DeactivateMultiDeviceIfAloneTaskData
}
