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

package ch.threema.domain.protocol.connection.data

import ch.threema.base.utils.SecureRandomUtil.generateRandomProtobufPadding
import ch.threema.protobuf.d2d.MdD2D


sealed interface D2dMessage {
    val bytes: ByteArray

    data class DeviceInfo(
        val platform: Platform,
        val platformDetails: String,
        val appVersion: String,
        val label: String
    ) : D2dMessage {
        enum class Platform(val value: Int) {
            UNSPECIFIED(0),
            ANDROID(1),
            IOS(2),
            DESKTOP(3),
            WEB(4)
        }

        override val bytes: ByteArray
            get() = MdD2D.DeviceInfo.newBuilder()
                .setPadding(generateRandomProtobufPadding())
                .setPlatformValue(platform.value)
                .setPlatformDetails(platformDetails)
                .setAppVersion(appVersion)
                .setLabel(label)
                .build()
                .toByteArray()
    }
}
