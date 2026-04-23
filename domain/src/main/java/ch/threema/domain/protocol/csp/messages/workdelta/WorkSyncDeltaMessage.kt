package ch.threema.domain.protocol.csp.messages.workdelta

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.csp.e2e.WorkSyncDelta
import ch.threema.protobuf.csp.e2e.fs.Version
import com.google.protobuf.InvalidProtocolBufferException

/**
 *  A special type of message sent by the special contact `*3MAW0RK` in work builds.
 *
 *  This message can never be sent by the app itself.
 */
class WorkSyncDeltaMessage(
    val workSyncDelta: WorkSyncDelta,
) : AbstractMessage() {

    override fun getType(): Int = ProtocolDefines.MSGTYPE_WORK_SYNC_DELTA

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution(): Boolean = false

    override fun exemptFromBlocking(): Boolean = true

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = false

    override fun reflectOutgoing(): Boolean = false

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    override fun getBody(): ByteArray = throw IllegalStateException("This message should never be sent by the app")

    companion object {

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): WorkSyncDeltaMessage {
            when {
                length < ProtocolDefines.MESSAGE_ID_LEN -> throw BadMessageException("Bad length ($length) for work sync delta message")
                offset < 0 -> throw BadMessageException("Bad offset ($offset) for work sync delta message")
                data.size < length + offset -> throw BadMessageException(
                    "Invalid byte array length (${data.size}) for offset $offset and length $length",
                )
            }
            val protobufPayload: ByteArray = data.copyOfRange(offset, offset + length)
            try {
                return WorkSyncDeltaMessage(WorkSyncDelta.parseFrom(protobufPayload))
            } catch (_: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid WorkSyncDelta protobuf data")
            }
        }
    }
}
