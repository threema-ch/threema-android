package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D

/**
 * A "throw-away" message that signals that the sender is currently typing a message or has
 * stopped typing (depending on the boolean flag `isTyping`).
 */
class TypingIndicatorMessage : AbstractMessage() {
    var isTyping: Boolean = false

    override fun getType() = ProtocolDefines.MSGTYPE_TYPING_INDICATOR

    override fun getMinimumRequiredForwardSecurityVersion() = Version.V1_1

    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = false

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = false

    override fun reflectIncoming() = true

    override fun reflectOutgoing() = false

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate() = false

    override fun getBody() = byteArrayOf(
        if (isTyping) {
            1.toByte()
        } else {
            0.toByte()
        },
    )

    override fun flagNoServerQueuing() = true

    override fun flagNoServerAck() = true

    companion object {
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromReflected(message: MdD2D.IncomingMessage): TypingIndicatorMessage {
            return fromByteArray(message.body.toByteArray()).apply {
                initializeCommonProperties(message)
            }
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray): TypingIndicatorMessage {
            return fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
            )
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(
            data: ByteArray,
            offset: Int,
            length: Int,
        ): TypingIndicatorMessage {
            when {
                length != 1 -> throw BadMessageException("Bad length ($length) for typing indicator message")
                offset < 0 -> throw BadMessageException("Bad offset ($offset) for typing indicator message")
                data.size < length + offset -> throw BadMessageException(
                    "Invalid byte array length (${data.size}) for offset $offset and length $length",
                )
            }

            return TypingIndicatorMessage().apply {
                isTyping = data[offset] > 0
            }
        }
    }
}
