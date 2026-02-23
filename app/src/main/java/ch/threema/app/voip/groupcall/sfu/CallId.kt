package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.CryptoCallUtils
import ch.threema.common.toHexString
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.storage.models.GroupModel
import java.nio.charset.StandardCharsets

data class CallId(val bytes: ByteArray) {
    companion object {
        fun create(group: GroupModel, callStartData: GroupCallStartData): CallId {
            return CallId(computeCallId(group, callStartData))
        }

        private fun computeCallId(group: GroupModel, callStartData: GroupCallStartData): ByteArray =
            CryptoCallUtils.gcBlake2b256(
                salt = CryptoCallUtils.SALT_CALL_ID,
                data = (
                    group.creatorIdentity.toByteArray(StandardCharsets.UTF_8) +
                        group.apiGroupId.groupId +
                        callStartData.protocolVersion.toByte() +
                        callStartData.gck +
                        callStartData.sfuBaseUrl.encodeToByteArray()
                    ),
            )
    }

    val hex: String by lazy {
        bytes.toHexString()
    }

    override fun toString(): String {
        return hex
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallId) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}
