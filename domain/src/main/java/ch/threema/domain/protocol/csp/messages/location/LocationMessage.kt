package ch.threema.domain.protocol.csp.messages.location

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.location.LocationMessage.Companion.fromByteArray
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
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
        fun fromReflected(message: IncomingMessage): LocationMessage {
            val locationMessage = fromByteArray(message.body.toByteArray())
            locationMessage.initializeCommonProperties(message)
            return locationMessage
        }

        @JvmStatic
        fun fromReflected(message: OutgoingMessage): LocationMessage {
            val locationMessage = fromByteArray(message.body.toByteArray())
            locationMessage.initializeCommonProperties(message)
            return locationMessage
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        private fun fromByteArray(data: ByteArray): LocationMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

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
