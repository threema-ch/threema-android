/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import org.apache.commons.io.EndianUtils
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val logger: Logger = LoggingUtil.getThreemaLogger("ContactSetPhotoMessage")

/**
 * A profile picture uploaded as a blob
 *
 * The contents are referenced by the `blobId`, the file `size` in bytes,
 * and the nonce to be used when decrypting the image blob.
 */
class SetProfilePictureMessage(
    @JvmField
    val blobId: ByteArray,
    @JvmField
    val size: Int,
    @JvmField
    val encryptionKey: ByteArray,
) : AbstractMessage() {

    override fun getType(): Int {
        return ProtocolDefines.MSGTYPE_CONTACT_SET_PHOTO
    }

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

    override fun getBody(): ByteArray {
        try {
            val bos = ByteArrayOutputStream()
            bos.write(blobId)
            EndianUtils.writeSwappedInteger(bos, size)
            bos.write(encryptionKey)
            return bos.toByteArray()
        } catch (e: Exception) {
            logger.error(e.message)
            return byteArrayOf()
        }
    }

    companion object {

        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): SetProfilePictureMessage =
            fromByteArray(
                data = message.body.toByteArray()
            ).apply {
                initializeCommonProperties(message)
            }

        @JvmStatic
        fun fromReflected(message: MdD2D.OutgoingMessage): SetProfilePictureMessage =
            fromByteArray(
                data = message.body.toByteArray()
            ).apply {
                initializeCommonProperties(message)
            }

        @JvmStatic
        fun fromByteArray(data: ByteArray): SetProfilePictureMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size
            )

        /**
         * Get the set profile picture message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @return the set profile picture message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): SetProfilePictureMessage {
            // Blob size is an int (4 bytes)
            val blobSizeLength = 4
            when {
                length != ProtocolDefines.BLOB_ID_LEN + blobSizeLength + ProtocolDefines.BLOB_KEY_LEN -> {
                    throw BadMessageException("Bad length ($length) for set profile picture message")
                }

                offset < 0 -> {
                    throw BadMessageException("Bad offset ($offset) for set profile picture message")
                }

                data.size < length + offset -> {
                    throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
                }
            }
            var readOffset = offset

            val blobId = data.copyOfRange(readOffset, readOffset + ProtocolDefines.BLOB_ID_LEN)
            readOffset += ProtocolDefines.BLOB_ID_LEN

            val blobSize = ByteBuffer.wrap(data, readOffset, blobSizeLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt()
            readOffset += blobSizeLength

            val encryptionKey =
                data.copyOfRange(readOffset, readOffset + ProtocolDefines.BLOB_KEY_LEN)

            return SetProfilePictureMessage(
                blobId = blobId,
                size = blobSize,
                encryptionKey = encryptionKey,
            )
        }
    }
}
