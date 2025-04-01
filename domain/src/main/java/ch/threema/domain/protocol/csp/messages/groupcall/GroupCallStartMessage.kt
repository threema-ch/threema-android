/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.groupcall

import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData.Companion.fromProtobuf
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufGroupMessage
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import java.nio.charset.StandardCharsets
import java.util.Arrays

class GroupCallStartMessage(payloadData: GroupCallStartData) :
    AbstractProtobufGroupMessage<GroupCallStartData>(
        ProtocolDefines.MSGTYPE_GROUP_CALL_START,
        payloadData
    ), GroupCallControlMessage {
    override fun flagSendPush() = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution() = true

    override fun exemptFromBlocking() = true

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = true

    override fun reflectIncoming() = true

    override fun reflectOutgoing() = true

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate(): Boolean = true

    companion object {

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group-call-start message
         *  @return Instance of [GroupCallStartMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): GroupCallStartMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val groupCallStartMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            groupCallStartMessage.initializeCommonProperties(message)
            return groupCallStartMessage
        }

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group-call-start message
         *  @return Instance of [GroupCallStartMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(
            message: MdD2D.OutgoingMessage,
            ownIdentity: String
        ): GroupCallStartMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val groupCallStartMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            groupCallStartMessage.initializeCommonProperties(message)
            groupCallStartMessage.fromIdentity = ownIdentity
            return groupCallStartMessage
        }

        /**
         * Build an instance of [GroupCallStartMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - header field: group-creator (identity, length 8)
         *  - header field: api-group-id (id, length 8)
         *  - body protobuf bytes of [GroupCallStartData]
         *
         * @param data   the data that represents the group-call-start message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [GroupCallStartMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): GroupCallStartMessage {
            if (length <= ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN) {
                throw BadMessageException("Bad length ($length) for group-call-start message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for group-call-start message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            var positionIndex = offset

            val groupCreator =
                String(data, positionIndex, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII)
            positionIndex += ProtocolDefines.IDENTITY_LEN

            val apiGroupId = GroupId(data, positionIndex)
            positionIndex += ProtocolDefines.GROUP_ID_LEN

            val protobufPayload = Arrays.copyOfRange(data, positionIndex, length + offset)
            val groupCallStartData: GroupCallStartData = fromProtobuf(protobufPayload)

            return GroupCallStartMessage(groupCallStartData).apply {
                setGroupCreator(groupCreator)
                setApiGroupId(apiGroupId)
            }
        }
    }
}
