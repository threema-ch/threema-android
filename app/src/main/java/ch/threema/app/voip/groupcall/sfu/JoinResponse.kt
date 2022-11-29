/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.sfu.webrtc.DtlsParameters
import ch.threema.app.voip.groupcall.sfu.webrtc.IceParameters
import ch.threema.app.voip.groupcall.sfu.webrtc.SessionParameters
import ch.threema.protobuf.groupcall.SfuHttpResponse

typealias JoinResponse = SfuResponse<JoinResponseBody>

data class JoinResponseBody(
    val startedAt: ULong,
    val maxParticipants: UInt,
    val participantId: ParticipantId,
    val iceUsernameFragment: String,
    val icePassword: String,
    val dtlsFingerprint: ByteArray,
    val addresses: List<Address>
) {
    companion object {
        fun fromSfuResponseBytes(bytes: ByteArray): JoinResponseBody {
            return SfuHttpResponse.Join.parseFrom(bytes).let {
                val addresses = it.addressesList.map { address ->
                    val protocol = when (address.protocol) {
                        SfuHttpResponse.Join.Address.Protocol.UDP -> Address.Protocol.UDP
                        else -> Address.Protocol.UNRECOGNIZED
                    }
                    Address(address.port, address.ip, protocol) }
                JoinResponseBody(
                    it.startedAt.toULong(),
                    it.maxParticipants.toUInt(),
                    ParticipantId(it.participantId.toUInt()),
                    it.iceUsernameFragment,
                    it.icePassword,
                    it.dtlsFingerprint.toByteArray(),
                    addresses
                )
            }
        }
    }
    data class Address (
        val port: Int,
        val ip: String,
        val protocol: Protocol
    ) {
        enum class Protocol {
            UDP, UNRECOGNIZED
        }

        val isIpv6: Boolean by lazy {
            ip.contains(':')
        }
    }

    val sessionParameters: SessionParameters
        get() = SessionParameters(
            participantId,
            IceParameters(iceUsernameFragment, icePassword),
            DtlsParameters(dtlsFingerprint)
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JoinResponseBody) return false

        if (maxParticipants != other.maxParticipants) return false
        if (participantId != other.participantId) return false
        if (iceUsernameFragment != other.iceUsernameFragment) return false
        if (icePassword != other.icePassword) return false
        if (!dtlsFingerprint.contentEquals(other.dtlsFingerprint)) return false
        if (addresses != other.addresses) return false

        return true
    }

    override fun hashCode(): Int {
        var result = maxParticipants.hashCode()
        result = 31 * result + participantId.hashCode()
        result = 31 * result + iceUsernameFragment.hashCode()
        result = 31 * result + icePassword.hashCode()
        result = 31 * result + dtlsFingerprint.contentHashCode()
        result = 31 * result + addresses.hashCode()
        return result
    }
}
