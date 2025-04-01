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

package ch.threema.domain.protocol.rendezvous

import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.join.MdD2DJoin
import ch.threema.protobuf.d2d.join.begin
import ch.threema.protobuf.d2d.join.edToNd
import ch.threema.protobuf.d2d.join.ndToEd
import ch.threema.protobuf.d2d.join.registered

sealed interface DeviceJoinMessage {
    val bytes: ByteArray

    @Suppress("CanSealedSubClassBeObject")
    class Begin : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                begin = begin {}
            }.toByteArray()
    }

    @Suppress("CanSealedSubClassBeObject")
    class Registered : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = ndToEd {
                registered = registered {}
            }.toByteArray()
    }

    data class BlobData(val data: Common.BlobData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                blobData = data
            }.toByteArray()
    }

    data class EssentialData(val data: MdD2DJoin.EssentialData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                essentialData = data
            }.toByteArray()
    }
}
