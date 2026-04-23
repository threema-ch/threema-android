package ch.threema.domain.protocol.csp.messages.file

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage.Companion.fromByteArray
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage.Companion.fromReflected
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("GroupFileMessage")

class GroupFileMessage : AbstractGroupMessage(), FileMessageInterface {
    override var fileData: FileData? = null

    override fun flagSendPush(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = true

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = true

    override fun getBody(): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            bos.write(groupCreator.toByteArray(StandardCharsets.US_ASCII))
            bos.write(apiGroupId.groupId)
            fileData!!.write(bos)
            bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_GROUP_FILE

    companion object {
        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group-file message
         *  @return Instance of [GroupFileMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: IncomingMessage): GroupFileMessage {
            val groupFileMessage = fromByteArray(message.body.toByteArray())
            groupFileMessage.initializeCommonProperties(message)
            return groupFileMessage
        }

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group-file message
         *  @return Instance of [GroupFileMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: OutgoingMessage): GroupFileMessage {
            val groupFileMessage = fromByteArray(message.body.toByteArray())
            groupFileMessage.initializeCommonProperties(message)
            return groupFileMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray): GroupFileMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        /**
         * Build an instance of [GroupFileMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - header field: group-creator (identity, length 8)
         *  - header field: api-group-id (id, length 8)
         *  - body json bytes of [FileData]
         *
         * @param data   the data that represents the group-file message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [GroupFileMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): GroupFileMessage {
            if (length <= ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN) {
                throw BadMessageException("Bad length ($length) for group-file message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for group-file message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            val groupFileMessage = GroupFileMessage()

            var positionIndex = offset
            groupFileMessage.groupCreator =
                String(data, positionIndex, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII)
            positionIndex += ProtocolDefines.IDENTITY_LEN

            groupFileMessage.apiGroupId = GroupId(data, positionIndex)
            positionIndex += ProtocolDefines.GROUP_ID_LEN

            val jsonObjectString =
                String(data, positionIndex, length + offset - positionIndex, StandardCharsets.UTF_8)
            groupFileMessage.fileData = FileData.parse(jsonObjectString)

            return groupFileMessage
        }
    }
}
