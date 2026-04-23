package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.sfu.webrtc.DtlsParameters
import ch.threema.app.voip.groupcall.sfu.webrtc.IceParameters
import ch.threema.app.voip.groupcall.sfu.webrtc.SessionParameters
import ch.threema.common.toHexString
import ch.threema.protobuf.group_call.SfuHttpResponse

typealias JoinResponse = SfuResponse<JoinResponseBody>

data class JoinResponseBody(
    val startedAt: ULong,
    val maxParticipants: UInt,
    val participantId: ParticipantId,
    val iceUsernameFragment: String,
    val icePassword: String,
    val dtlsFingerprint: ByteArray,
    val addresses: List<Address>,
    val rtpHeaderExtensionIds: RtpHeaderExtensionIds,
) {
    data class Address(
        val port: Int,
        val ip: String,
        val protocol: Protocol,
    ) {
        enum class Protocol {
            UDP,
            UNRECOGNIZED,
        }

        val isIpv6: Boolean by lazy {
            ip.contains(':')
        }
    }

    val sessionParameters: SessionParameters
        get() = SessionParameters(
            participantId = participantId,
            iceParameters = IceParameters(iceUsernameFragment, icePassword),
            dtlsParameters = DtlsParameters(dtlsFingerprint),
            rtpHeaderExtensionIds = rtpHeaderExtensionIds,
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

    override fun toString() =
        "JoinResponseBody(startedAt=$startedAt, maxParticipants=$maxParticipants, participantId=$participantId, " +
            "iceUsernameFragment='$iceUsernameFragment', icePassword='$icePassword', " +
            "dtlsFingerprint=${dtlsFingerprint.toHexString()}, addresses=$addresses)"

    companion object {
        fun fromSfuResponseBytes(bytes: ByteArray): JoinResponseBody {
            val joinResponse = SfuHttpResponse.Join.parseFrom(bytes)
            return JoinResponseBody(
                startedAt = joinResponse.startedAt.toULong(),
                maxParticipants = joinResponse.maxParticipants.toUInt(),
                participantId = ParticipantId(joinResponse.participantId.toUInt()),
                iceUsernameFragment = joinResponse.iceUsernameFragment,
                icePassword = joinResponse.icePassword,
                dtlsFingerprint = joinResponse.dtlsFingerprint.toByteArray(),
                addresses = joinResponse.addressesList.toAddresses(),
                rtpHeaderExtensionIds = RtpHeaderExtensionIds.createFromJoinResponse(joinResponse),
            )
        }

        private fun List<SfuHttpResponse.Join.Address>.toAddresses(): List<Address> =
            map { address ->
                val protocol = when (address.protocol) {
                    SfuHttpResponse.Join.Address.Protocol.UDP -> Address.Protocol.UDP
                    else -> Address.Protocol.UNRECOGNIZED
                }
                Address(address.port, address.ip, protocol)
            }
    }
}
