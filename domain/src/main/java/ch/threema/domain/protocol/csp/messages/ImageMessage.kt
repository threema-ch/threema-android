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

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import com.neilalexander.jnacl.NaCl
import org.apache.commons.io.EndianUtils

/**
 * A message that has an image (stored on the blob server) as its content.
 *
 * The contents are referenced by the `blobId`, the file `size` in bytes,
 * and the nonce to be used when decrypting the image blob.
 */
@Deprecated(
    message = "Use the generic FileMessage instead",
    replaceWith = ReplaceWith(
        expression = "FileMessage()",
        imports = ["ch.threema.domain.protocol.csp.messages.file.FileMessage"],
    ),
)
class ImageMessage(
    @JvmField val blobId: ByteArray,
    @JvmField val size: Int,
    @JvmField val nonce: ByteArray,
) : AbstractMessage() {
    override fun getType(): Int = ProtocolDefines.MSGTYPE_IMAGE

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

    override fun getBody(): ByteArray {
        val body =
            ByteArray(ProtocolDefines.BLOB_ID_LEN + IMAGE_SIZE_INT_BYTE_LENGTH + NaCl.NONCEBYTES)
        System.arraycopy(blobId, 0, body, 0, ProtocolDefines.BLOB_ID_LEN)
        EndianUtils.writeSwappedInteger(body, ProtocolDefines.BLOB_ID_LEN, size)
        System.arraycopy(
            nonce,
            0,
            body,
            ProtocolDefines.BLOB_ID_LEN + IMAGE_SIZE_INT_BYTE_LENGTH,
            nonce.size,
        )
        return body
    }

    companion object {
        private const val IMAGE_SIZE_INT_BYTE_LENGTH = 4

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the image message
         *  @return Instance of [ImageMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): ImageMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val imageMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            imageMessage.initializeCommonProperties(message)
            return imageMessage
        }

        /**
         * Build an instance of [ImageMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - blob-id (length 16)
         *  - image size int (length 4)
         *  - nonce (length 24)
         *
         * @param data   the data that represents the image message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [ImageMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): ImageMessage {
            if (length < (ProtocolDefines.BLOB_ID_LEN + IMAGE_SIZE_INT_BYTE_LENGTH + NaCl.NONCEBYTES)) {
                throw BadMessageException("Bad length ($length) for image message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for image message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            var positionIndex = offset

            val blobId = ByteArray(ProtocolDefines.BLOB_ID_LEN)
            System.arraycopy(data, positionIndex, blobId, 0, ProtocolDefines.BLOB_ID_LEN)
            positionIndex += ProtocolDefines.BLOB_ID_LEN

            val imageSize: Int = EndianUtils.readSwappedInteger(data, positionIndex)
            positionIndex += IMAGE_SIZE_INT_BYTE_LENGTH

            val nonce = ByteArray(NaCl.NONCEBYTES)
            System.arraycopy(data, positionIndex, nonce, 0, nonce.size)

            return ImageMessage(
                blobId = blobId,
                size = imageSize,
                nonce = nonce,
            )
        }
    }
}
