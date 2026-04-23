package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.GroupEditMessage.Companion.fromByteArray
import ch.threema.domain.protocol.csp.messages.GroupEditMessage.Companion.fromReflected
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufGroupMessage
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.nio.charset.StandardCharsets

/**
 *  This does **not** edit groups.
 *  It edits messages within a group chat.
 */
class GroupEditMessage(payloadData: EditMessageData) :
    AbstractProtobufGroupMessage<EditMessageData>(
        ProtocolDefines.MSGTYPE_GROUP_EDIT_MESSAGE,
        payloadData,
    ) {
    override fun getMinimumRequiredForwardSecurityVersion() = Version.V1_2

    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = false

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = true

    override fun reflectIncoming() = true

    override fun reflectOutgoing() = true

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate() = false

    override fun flagSendPush() = true

    companion object {
        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group edit message
         *  @return Instance of [GroupEditMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: IncomingMessage): GroupEditMessage {
            val groupEditMessage = fromByteArray(message.body.toByteArray())
            groupEditMessage.initializeCommonProperties(message)
            return groupEditMessage
        }

        @JvmStatic
        fun fromReflected(message: OutgoingMessage): GroupEditMessage {
            val groupEditMessage = fromByteArray(message.body.toByteArray())
            groupEditMessage.initializeCommonProperties(message)
            return groupEditMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        private fun fromByteArray(data: ByteArray): GroupEditMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        /**
         * Build an instance of [GroupEditMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - header field: group-creator (identity, length 8)
         *  - header field: api-group-id (id, length 8)
         *  - body fields: see [EditMessageData.fromProtobuf]
         *
         * @param data   the data that represents the group-edit message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [GroupEditMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): GroupEditMessage {
            when {
                length < ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + ProtocolDefines.MESSAGE_ID_LEN -> {
                    throw BadMessageException("Bad length ($length) for group-edit-message")
                }

                offset < 0 -> throw BadMessageException("Bad offset ($offset) for group-edit-message")
                data.size < length + offset -> throw BadMessageException(
                    "Invalid byte array length (${data.size}) for offset $offset and length $length",
                )
            }

            var positionIndex = offset
            val groupCreator =
                String(data, positionIndex, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII)
            positionIndex += ProtocolDefines.IDENTITY_LEN

            val apiGroupId = GroupId(data, positionIndex)
            positionIndex += ProtocolDefines.GROUP_ID_LEN

            val protobufPayload: ByteArray = data.copyOfRange(positionIndex, offset + length)
            val editMessageData: EditMessageData = EditMessageData.fromProtobuf(protobufPayload)
            return GroupEditMessage(editMessageData).apply {
                this.groupCreator = groupCreator
                this.apiGroupId = apiGroupId
            }
        }
    }
}
