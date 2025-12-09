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
import ch.threema.common.readLittleEndianInt
import ch.threema.common.readLittleEndianShort
import ch.threema.common.writeLittleEndianInt
import ch.threema.common.writeLittleEndianShort
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

private val logger = getThreemaLogger("VideoMessage")

/**
 * A message that has a video including thumbnail (stored on the blob server) as its content.
 *
 * The contents are referenced by the `videoBlobId`/`thumbnailBlobId`,
 * the `videoSize`/`thumbnailSize` in bytes, and the `encryptionKey`
 * to be used when decrypting the video blob.
 *
 * The thumbnail uses the same key, the nonces are as follows:
 *
 * Video:     0x000000000000000000000000000000000000000000000001
 * Thumbnail: 0x000000000000000000000000000000000000000000000002
 *
 */
@Deprecated(
    message = "Use the generic FileMessage instead",
    replaceWith = ReplaceWith(
        expression = "FileMessage()",
        imports = ["ch.threema.domain.protocol.csp.messages.file.FileMessage"],
    ),
)
class VideoMessage(
    @JvmField val durationInSeconds: Int,
    @JvmField val videoBlobId: ByteArray,
    @JvmField val videoSizeInBytes: Int,
    @JvmField val thumbnailBlobId: ByteArray,
    @JvmField val thumbnailSizeInBytes: Int,
    @JvmField val encryptionKey: ByteArray,
) : AbstractMessage() {
    override fun getType(): Int = ProtocolDefines.MSGTYPE_VIDEO

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

    override fun getBody(): ByteArray? {
        val bos = ByteArrayOutputStream()
        try {
            bos.writeLittleEndianShort(durationInSeconds.toShort())
            bos.write(videoBlobId)
            bos.writeLittleEndianInt(videoSizeInBytes)
            bos.write(thumbnailBlobId)
            bos.writeLittleEndianInt(thumbnailSizeInBytes)
            bos.write(encryptionKey)
            return bos.toByteArray()
        } catch (ioException: IOException) {
            logger.error("Cannot create body of message", ioException)
            return null
        }
    }

    companion object {
        private const val VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
        private const val VIDEO_SIZE_INT_BYTE_LENGTH = 4
        private const val THUMBNAIL_SIZE_INT_BYTE_LENGTH = 4

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the video message
         *  @return Instance of [VideoMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): VideoMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val videoMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            videoMessage.initializeCommonProperties(message)
            return videoMessage
        }

        /**
         * Build an instance of [VideoMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - video-length short (length 2)
         *  - video-blob-id (length 16)
         *  - video-size int (length 4)
         *  - thumbnail-blob-id (length 16)
         *  - thumbnail-size int (length 4)
         *  - encryption-key (length 32)
         *
         * @param data   the data that represents the video message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [VideoMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): VideoMessage {
            val minLength = VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH +
                ProtocolDefines.BLOB_ID_LEN + VIDEO_SIZE_INT_BYTE_LENGTH +
                ProtocolDefines.BLOB_ID_LEN + THUMBNAIL_SIZE_INT_BYTE_LENGTH +
                ProtocolDefines.BLOB_KEY_LEN
            if (length < minLength) {
                throw BadMessageException("Bad length ($length) for video message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for video message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            val bis = ByteArrayInputStream(data, offset, length)

            try {
                val durationInSeconds: Short = bis.readLittleEndianShort()

                val videoBlobId = ByteArray(ProtocolDefines.BLOB_ID_LEN)
                bis.read(videoBlobId)

                val videoSizeInBytes: Int = bis.readLittleEndianInt()

                val thumbnailBlobId = ByteArray(ProtocolDefines.BLOB_ID_LEN)
                bis.read(thumbnailBlobId)

                val thumbnailSizeInBytes: Int = bis.readLittleEndianInt()

                val encryptionKey = ByteArray(ProtocolDefines.BLOB_KEY_LEN)
                bis.read(encryptionKey)

                return VideoMessage(
                    durationInSeconds = durationInSeconds.toInt(),
                    videoBlobId = videoBlobId,
                    videoSizeInBytes = videoSizeInBytes,
                    thumbnailBlobId = thumbnailBlobId,
                    thumbnailSizeInBytes = thumbnailSizeInBytes,
                    encryptionKey = encryptionKey,
                )
            } catch (ioException: IOException) {
                throw BadMessageException("Message body contents failed to parse", ioException)
            }
        }
    }
}
