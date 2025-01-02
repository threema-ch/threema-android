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

import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.MessageFilterInstruction

class DropDeviceTask(private val deviceId: DeviceId) : ActiveTask<Unit> {
    override val type: String = "DropDeviceTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        handle.write(OutboundD2mMessage.DropDevice(deviceId))
        handle.read {
            when (it) {
                is InboundD2mMessage.DropDeviceAck -> if (it.deviceId == deviceId) {
                    MessageFilterInstruction.ACCEPT
                } else {
                    MessageFilterInstruction.BYPASS_OR_BACKLOG
                }
                else -> MessageFilterInstruction.BYPASS_OR_BACKLOG
            }
        }
    }
}
