/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

sealed class OutgoingD2mMessageTask : ActiveTask<Unit>, PersistableTask {
    protected suspend fun sendD2mMessage(message: OutboundD2mMessage, handle: ActiveTaskCodec) {
        handle.write(message)
    }
}

class OutgoingDropDeviceTask(private val deviceId: DeviceId) : OutgoingD2mMessageTask() {
    override val type: String = "OutgoingDropDeviceTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        sendD2mMessage(OutboundD2mMessage.DropDevice(deviceId), handle)
    }

    override fun serialize(): SerializableTaskData =
        OutgoingDropDeviceData(deviceId.id)

    @Serializable
    data class OutgoingDropDeviceData(private val deviceId: ULong) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingDropDeviceTask(DeviceId(deviceId))
    }
}
