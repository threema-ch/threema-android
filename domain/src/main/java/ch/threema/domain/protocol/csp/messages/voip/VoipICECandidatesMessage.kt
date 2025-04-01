/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.voip

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.d2d.MdD2D
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger: Logger = LoggingUtil.getThreemaLogger("VoipICECandidatesMessage")

class VoipICECandidatesMessage : VoipMessage() {

    var data: VoipICECandidatesData? = null

    override fun getBody(): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            data!!.write(bos)
            bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_VOIP_ICE_CANDIDATES

    override fun allowUserProfileDistribution(): Boolean = false

    override fun exemptFromBlocking(): Boolean = false

    override fun protectAgainstReplay(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = false

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    companion object {

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the voip-ice-candidates message
         *  @return Instance of [VoipICECandidatesMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): VoipICECandidatesMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val voipICECandidatesMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            voipICECandidatesMessage.initializeCommonProperties(message)
            return voipICECandidatesMessage
        }

        /**
         * Build an instance of [VoipICECandidatesMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - body json bytes of [VoipICECandidatesData]
         *
         * @param data   the data that represents the voip-ice-candidates message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [VoipICECandidatesMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): VoipICECandidatesMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for voip-ice-candidates message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for voip-ice-candidates message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }
            return VoipICECandidatesMessage().apply {
                val json = String(data, offset, length, StandardCharsets.UTF_8)
                this.data = VoipICECandidatesData.parse(json)
            }
        }
    }
}
