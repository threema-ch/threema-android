package ch.threema.app.voip.groupcall.sfu

import ch.threema.protobuf.group_call.SfuHttpResponse

typealias PeekResponse = SfuResponse<PeekResponseBody>

data class PeekResponseBody(
    val startedAt: ULong,
    val maxParticipants: UInt,
    val encryptedCallState: ByteArray?,
) {
    companion object {
        fun fromSfuResponseBytes(bytes: ByteArray): PeekResponseBody {
            return SfuHttpResponse.Peek.parseFrom(bytes)
                .let {
                    PeekResponseBody(
                        it.startedAt.toULong(),
                        it.maxParticipants.toUInt(),
                        it.encryptedCallState?.toByteArray(),
                    )
                }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeekResponseBody) return false

        if (startedAt != other.startedAt) return false
        if (maxParticipants != other.maxParticipants) return false
        if (encryptedCallState != null) {
            if (other.encryptedCallState == null) return false
            if (!encryptedCallState.contentEquals(other.encryptedCallState)) return false
        } else if (other.encryptedCallState != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = startedAt.hashCode()
        result = 31 * result + maxParticipants.hashCode()
        result = 31 * result + (encryptedCallState?.contentHashCode() ?: 0)
        return result
    }

    override fun toString() =
        "PeekResponseBody(startedAt=$startedAt, maxParticipants=$maxParticipants, encryptedCallState=${encryptedCallState?.let { "*********" }})"
}
