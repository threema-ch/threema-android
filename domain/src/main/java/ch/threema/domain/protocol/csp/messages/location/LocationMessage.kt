/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import java.nio.charset.StandardCharsets

/**
 * A message that has a GPS location with accuracy as its contents.
 *
 * Coordinates are in WGS 84, accuracy is in meters.
 */
class LocationMessage(private val locationMessageData: LocationMessageData) : AbstractMessage() {

    val latitude: Double
        get() = locationMessageData.latitude

    val longitude: Double
        get() = locationMessageData.longitude

    val accuracy: Double?
        get() = locationMessageData.accuracy

    val poi: Poi?
        get() = locationMessageData.poi

    override fun getType(): Int = ProtocolDefines.MSGTYPE_LOCATION

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

    override fun getBody(): ByteArray =
        locationMessageData.toBodyString().toByteArray(StandardCharsets.UTF_8)

    companion object {

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the location message
         *  @return Instance of [LocationMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): LocationMessage {
            val locationMessage = fromByteArray(message.body.toByteArray())
            locationMessage.initializeCommonProperties(message)
            return locationMessage
        }

        @JvmStatic
        fun fromReflected(message: MdD2D.OutgoingMessage): LocationMessage {
            val locationMessage = fromByteArray(message.body.toByteArray())
            locationMessage.initializeCommonProperties(message)
            return locationMessage
        }

        @JvmStatic
        private fun fromByteArray(data: ByteArray): LocationMessage =
            fromByteArray(data, 0, data.size)

        /**
         * Build an instance of [LocationMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * @param data   the data that represents the location message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [LocationMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see LocationMessageData.parse
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): LocationMessage {
            val locationMessageData = LocationMessageData.parse(data, offset, length)
            return LocationMessage(locationMessageData)
        }
    }
}
