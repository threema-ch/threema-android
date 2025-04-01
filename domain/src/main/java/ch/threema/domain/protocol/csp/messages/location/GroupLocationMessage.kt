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

package ch.threema.domain.protocol.csp.messages.location

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger: Logger = LoggingUtil.getThreemaLogger("GroupLocationMessage")

/**
 * A group message that has a GPS location with accuracy as its contents.
 *
 * Coordinates are in WGS 84, accuracy is in meters.
 */
class GroupLocationMessage(private val locationMessageData: LocationMessageData) :
    AbstractGroupMessage() {

    val latitude: Double
        get() = locationMessageData.latitude

    val longitude: Double
        get() = locationMessageData.longitude

    val accuracy: Double?
        get() = locationMessageData.accuracy

    val poi: Poi?
        get() = locationMessageData.poi

    override fun getType(): Int = ProtocolDefines.MSGTYPE_GROUP_LOCATION

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
        try {
            val bos = ByteArrayOutputStream()
            bos.write(groupCreator.toByteArray(StandardCharsets.US_ASCII))
            bos.write(apiGroupId.groupId)
            bos.write(locationMessageData.toBodyString().toByteArray(StandardCharsets.UTF_8))
            return bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            return null
        }
    }

    companion object {

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the group-location message
         *  @return Instance of [GroupLocationMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): GroupLocationMessage {
            val groupLocationMessage = fromByteArray(message.body.toByteArray())
            groupLocationMessage.initializeCommonProperties(message)
            return groupLocationMessage
        }

        @JvmStatic
        fun fromReflected(message: MdD2D.OutgoingMessage): GroupLocationMessage {
            val groupLocationMessage = fromByteArray(message.body.toByteArray())
            groupLocationMessage.initializeCommonProperties(message)
            return groupLocationMessage
        }

        @JvmStatic
        fun fromByteArray(data: ByteArray): GroupLocationMessage = fromByteArray(data, 0, data.size)

        /**
         * Build an instance of [GroupLocationMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *   - header field: group-creator (identity, length 8)
         *   - header field: api-group-id (id, length 8)
         *   - body string bytes of [LocationMessageData]
         *
         * @param data   the data that represents the group-location message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [GroupLocationMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see LocationMessageData.parse
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): GroupLocationMessage {
            val minHeaderLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN
            val minBodyLength = LocationMessageData.MINIMUM_REQUIRED_BYTES
            if (length < (minHeaderLength + minBodyLength)) {
                throw BadMessageException("Bad length ($length) for group location message")
            }


            var positionIndex = offset
            val groupCreator =
                String(data, positionIndex, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII)
            positionIndex += ProtocolDefines.IDENTITY_LEN

            val apiGroupId = GroupId(data, positionIndex)
            positionIndex += ProtocolDefines.GROUP_ID_LEN

            val locationMessageData = LocationMessageData.parse(
                data = data,
                offset = positionIndex,
                length = length + offset - positionIndex
            )

            return GroupLocationMessage(locationMessageData).apply {
                this.groupCreator = groupCreator
                this.apiGroupId = apiGroupId
            }
        }
    }
}
