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

package ch.threema.domain.protocol.csp.messages.ballot

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("GroupPollSetupMessage")

/**
 * A group poll creation message.
 */
class GroupPollSetupMessage : AbstractGroupMessage(), BallotSetupInterface {
    override var ballotId: BallotId? = null
    override var ballotCreatorIdentity: String? = null
    override var ballotData: BallotData? = null

    // this is only used for debugging
    @JvmField
    var rawBallotData: String? = null

    override fun flagSendPush(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = true

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = true

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = true

    override fun getBody(): ByteArray? {
        if (ballotId == null || ballotData == null) {
            return null
        }
        try {
            val bos = ByteArrayOutputStream()
            bos.write(groupCreator.toByteArray(StandardCharsets.US_ASCII))
            bos.write(apiGroupId.groupId)
            bos.write(ballotId!!.ballotId)
            ballotData!!.write(bos)
            return bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            return null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_GROUP_BALLOT_CREATE

    companion object {
        @JvmStatic
        fun fromReflected(
            message: MdD2D.IncomingMessage,
            fromIdentity: String,
        ): GroupPollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            fromIdentity = fromIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        fun fromReflected(
            message: MdD2D.OutgoingMessage,
            fromIdentity: String,
        ): GroupPollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            fromIdentity = fromIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, fromIdentity: String): GroupPollSetupMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
                fromIdentity = fromIdentity,
            )

        /**
         * Get the group poll-setup message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @param fromIdentity the identity of the sender
         * @return the poll-setup message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(
            data: ByteArray,
            offset: Int,
            length: Int,
            fromIdentity: String,
        ): GroupPollSetupMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for poll setup message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for poll setup message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            return GroupPollSetupMessage().apply {
                ballotCreatorIdentity = fromIdentity

                var positionIndex = offset
                groupCreator = String(
                    data,
                    positionIndex,
                    ProtocolDefines.IDENTITY_LEN,
                    StandardCharsets.US_ASCII,
                )
                positionIndex += ProtocolDefines.IDENTITY_LEN

                apiGroupId = GroupId(data, positionIndex)
                positionIndex += ProtocolDefines.GROUP_ID_LEN

                ballotId = BallotId(data, positionIndex)
                positionIndex += ProtocolDefines.BALLOT_ID_LEN

                val jsonObjectString = String(
                    data,
                    positionIndex,
                    length + offset - positionIndex,
                    StandardCharsets.UTF_8,
                )
                ballotData = BallotData.parse(jsonObjectString)

                // this is only used for debugging
                rawBallotData = jsonObjectString
            }
        }
    }
}
