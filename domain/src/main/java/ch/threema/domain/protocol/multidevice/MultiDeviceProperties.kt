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
    val serverInfoListener: (serverInfo: InboundD2mMessage.ServerInfo) -> Unit
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

    override fun toString(): String {
        return "MultiDeviceProperties(registrationTime=$registrationTime, mediatorDeviceId=$mediatorDeviceId, cspDeviceId=$cspDeviceId, keys=********, deviceInfo=$deviceInfo, protocolVersion=$protocolVersion, serverInfoListener=$serverInfoListener, deviceSlotState=$deviceSlotState)"
    }
}
