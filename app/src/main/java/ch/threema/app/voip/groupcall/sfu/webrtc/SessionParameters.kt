/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.webrtc

import ch.threema.app.voip.groupcall.sfu.ParticipantId
import ch.threema.base.utils.Utils

data class SessionParameters(
    val participantId: ParticipantId,
    val iceParameters: IceParameters,
    val dtlsParameters: DtlsParameters
)

data class IceParameters(val usernameFragment: String, val password: String)

data class DtlsParameters(val fingerprint: ByteArray) {
    fun fingerprintToString(): String = Utils.byteArrayToSeparatedHexString(fingerprint, ':')

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DtlsParameters) return false

        if (!fingerprint.contentEquals(other.fingerprint)) return false

        return true
    }

    override fun hashCode(): Int {
        return fingerprint.contentHashCode()
    }
}
