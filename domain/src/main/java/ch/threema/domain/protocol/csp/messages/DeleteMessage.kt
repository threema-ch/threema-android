/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.DeleteMessageData.Companion.fromProtobuf
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufMessage
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D

class DeleteMessage(payloadData: DeleteMessageData) : AbstractProtobufMessage<DeleteMessageData>(
    ProtocolDefines.MSGTYPE_DELETE_MESSAGE,
    payloadData,
) {
    override fun getMinimumRequiredForwardSecurityVersion() = Version.V1_1

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
        const val DELETE_MESSAGES_MAX_AGE: Long = 6L * 60L * 60L * 1000L

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the delete message
         *  @return Instance of [DeleteMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): DeleteMessage {
            val deleteMessage = fromByteArray(message.body.toByteArray())
            deleteMessage.initializeCommonProperties(message)
            return deleteMessage
        }

        @JvmStatic
        fun fromReflected(message: MdD2D.OutgoingMessage): DeleteMessage {
            val deleteMessage = fromByteArray(message.body.toByteArray())
            deleteMessage.initializeCommonProperties(message)
            return deleteMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        private fun fromByteArray(data: ByteArray): DeleteMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        /**
         * Build an instance of [DeleteMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - @see [DeleteMessageData.fromProtobuf]
         *
         * @param data   the data that represents the delete message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [DeleteMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): DeleteMessage {
            when {
                length < ProtocolDefines.MESSAGE_ID_LEN -> throw BadMessageException("Bad length ($length) for delete message")
                offset < 0 -> throw BadMessageException("Bad offset ($offset) for delete message")
                data.size < length + offset -> throw BadMessageException(
                    "Invalid byte array length (${data.size}) for offset $offset and length $length",
                )
            }
            val protobufPayload: ByteArray = data.copyOfRange(offset, offset + length)
            val deleteMessageData: DeleteMessageData = fromProtobuf(protobufPayload)
            return DeleteMessage(deleteMessageData)
        }
    }
}
