package ch.threema.app.voip.groupcall.sfu.webrtc

import ch.threema.app.voip.groupcall.sfu.ParticipantId
import ch.threema.base.utils.Utils

data class SessionParameters(
    val participantId: ParticipantId,
    val iceParameters: IceParameters,
    val dtlsParameters: DtlsParameters,
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
