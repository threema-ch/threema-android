/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.conversationOrNull
import ch.threema.protobuf.d2d.groupOrNull
import java.io.ByteArrayOutputStream

private val logger = getThreemaLogger("GroupSyncRequestMessage")

/**
 * Request current group information to be sent back.
 */
class GroupSyncRequestMessage : AbstractGroupMessage() {
    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution(): Boolean = false

    override fun exemptFromBlocking(): Boolean = true

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    override fun getType(): Int = ProtocolDefines.MSGTYPE_GROUP_REQUEST_SYNC

    override fun getBody(): ByteArray? {
        try {
            val bos = ByteArrayOutputStream()
            bos.write(apiGroupId.groupId)
            return bos.toByteArray()
        } catch (e: Exception) {
            logger.error(e.message)
            return null
        }
    }

    companion object {
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromReflected(
            message: MdD2D.IncomingMessage,
            myIdentity: Identity,
        ): GroupSyncRequestMessage {
            val groupSyncRequestMessage = fromByteArray(message.body.toByteArray(), myIdentity)
            groupSyncRequestMessage.initializeCommonProperties(message)
            return groupSyncRequestMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromReflected(message: MdD2D.OutgoingMessage): GroupSyncRequestMessage {
            val creatorIdentity = message.conversationOrNull?.groupOrNull?.creatorIdentity
                ?: throw BadMessageException("Group conversation is not set on group-sync-request message")
            val groupSyncRequestMessage = fromByteArray(message.body.toByteArray(), creatorIdentity)
            groupSyncRequestMessage.initializeCommonProperties(message)
            return groupSyncRequestMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, creatorIdentity: Identity): GroupSyncRequestMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
                creatorIdentity = creatorIdentity,
            )

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(
            data: ByteArray,
            offset: Int,
            length: Int,
            creatorIdentity: Identity,
        ): GroupSyncRequestMessage {
            when {
                length != ProtocolDefines.GROUP_ID_LEN -> throw BadMessageException("Bad length ($length) for group-sync-request message")
                offset < 0 -> throw BadMessageException("Bad offset ($offset) for group-sync-request message")
                data.size < length + offset -> throw BadMessageException(
                    "Invalid byte array length (${data.size}) for offset $offset and length $length",
                )
            }

            return GroupSyncRequestMessage().apply {
                groupCreator = creatorIdentity
                apiGroupId = GroupId(data, offset)
            }
        }
    }
}
