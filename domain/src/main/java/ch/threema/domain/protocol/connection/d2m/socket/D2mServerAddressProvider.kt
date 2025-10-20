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

package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.common.toHexString
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.protobuf.d2m.MdD2M
import com.google.protobuf.ByteString

class D2mServerAddressProvider(
    private val serverAddressProvider: ServerAddressProvider,
    private val dgid: ByteArray,
    private val serverGroup: String,
) {
    fun get(): String {
        val mediatorUrl = serverAddressProvider.getMediatorUrl().get(dgid)

        val clientUrlInfo = MdD2M.ClientUrlInfo.newBuilder()
            .setDeviceGroupId(ByteString.copyFrom(dgid))
            .setServerGroup(serverGroup)
            .build()
            .toByteArray()
            .toHexString()

        return "$mediatorUrl/$clientUrlInfo"
    }
}
