package ch.threema.domain.protocol.csp.messages.file

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.file.FileMessage.Companion.fromByteArray
import ch.threema.domain.protocol.csp.messages.file.FileMessage.Companion.fromReflected
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("FileMessage")

class FileMessage : AbstractMessage(), FileMessageInterface {
    override var fileData: FileData? = null

    override fun flagSendPush(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_0

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = true

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = true

    override fun sendAutomaticDeliveryReceipt(): Boolean = true

    override fun bumpLastUpdate(): Boolean = true

    override fun getBody(): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            fileData!!.write(bos)
            bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_FILE

    companion object {
        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the file message
         *  @return Instance of [FileMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: IncomingMessage): FileMessage {
            val fileMessage = fromByteArray(message.body.toByteArray())
            fileMessage.initializeCommonProperties(message)
            return fileMessage
        }

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the file message
         *  @return Instance of [FileMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: OutgoingMessage): FileMessage {
            val fileMessage = fromByteArray(message.body.toByteArray())
            fileMessage.initializeCommonProperties(message)
            return fileMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray): FileMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        /**
         * Build an instance of [FileMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - body json bytes of [FileData]
         *
         * @param data   the data that represents the file message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [FileMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): FileMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for file message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for file message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }
            return FileMessage().apply {
                val json = String(data, offset, length, StandardCharsets.UTF_8)
                fileData = FileData.parse(json)
            }
        }
    }
}
