package ch.threema.domain.protocol.csp.messages.voip

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage.Companion.fromByteArray
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage.Companion.fromReflected
import ch.threema.protobuf.d2d.IncomingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("VoipCallRingingMessage")

/**
 * This packet is sent by the receiver of a call.
 * It indicates towards the caller that the phone is ringing.
 */
class VoipCallRingingMessage : VoipMessage() {
    var data: VoipCallRingingData? = null

    override fun getType(): Int = ProtocolDefines.MSGTYPE_VOIP_CALL_RINGING

    override fun getBody(): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            data!!.write(bos)
            bos.toByteArray()
        } catch (exception: Exception) {
            logger.error("Could not serialize VoipCallRingingMessage", exception)
            null
        }
    }

    override fun allowUserProfileDistribution(): Boolean = false

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    companion object {
        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the voip-call-ringing message
         *  @return Instance of [VoipCallRingingMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: IncomingMessage): VoipCallRingingMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val voipCallRingingMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            voipCallRingingMessage.initializeCommonProperties(message)
            return voipCallRingingMessage
        }

        /**
         * Build an instance of [VoipCallRingingMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - body json bytes of [VoipCallRingingData]
         *
         * @param data   the data that represents the voip-call-ringing message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [VoipCallRingingMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): VoipCallRingingMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for voip-call-ringing message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for voip-call-ringing message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }
            return VoipCallRingingMessage().apply {
                val json = String(data, offset, length, StandardCharsets.UTF_8)
                this.data = VoipCallRingingData.parse(json)
            }
        }
    }
}
