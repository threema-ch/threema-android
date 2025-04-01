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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D

class DeleteProfilePictureMessage : AbstractMessage() {
    override fun getType(): Int = ProtocolDefines.MSGTYPE_CONTACT_DELETE_PHOTO

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_1

    override fun allowUserProfileDistribution(): Boolean = false

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    override fun getBody(): ByteArray = ByteArray(0)

    companion object {

        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): DeleteProfilePictureMessage =
            fromByteArray(
                data = message.body.toByteArray()
            ).apply {
                initializeCommonProperties(message)
            }

        @JvmStatic
        fun fromReflected(message: MdD2D.OutgoingMessage): DeleteProfilePictureMessage =
            fromByteArray(
                data = message.body.toByteArray()
            ).apply {
                initializeCommonProperties(message)
            }

        @JvmStatic
        fun fromByteArray(data: ByteArray): DeleteProfilePictureMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size
            )

        /**
         * Get the delete profile picture message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @return the delete profile picture message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): DeleteProfilePictureMessage =
            when {
                length > 0 -> {
                    throw BadMessageException("Bad length ($length) for delete profile picture message")
                }

                offset < 0 -> {
                    throw BadMessageException("Bad offset ($offset) for delete profile picture message")
                }

                data.size < length + offset -> {
                    throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
                }

                else -> DeleteProfilePictureMessage()
            }
    }
}
