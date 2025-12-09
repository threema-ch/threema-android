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

private val logger = getThreemaLogger("AudioMessage")

/**
 * A message that has an audio recording (stored on the blob server) as its content.
 *
 * The contents are referenced by the `audioDuration`, `audioBlobId`, the `audioSize` in bytes, and the
 * `encryptionKey` in bytes to be used when decrypting the audio blob.
 */
@Deprecated(
    message = "Use the generic FileMessage instead",
    replaceWith = ReplaceWith(
        expression = "FileMessage()",
        imports = ["ch.threema.domain.protocol.csp.messages.file.FileMessage"],
    ),
)
class AudioMessage(
    @JvmField val durationInSeconds: Int,
    @JvmField val audioBlobId: ByteArray,
    @JvmField val audioSizeInBytes: Int,
    @JvmField val encryptionKey: ByteArray,
) : AbstractMessage() {
    override fun getType(): Int = ProtocolDefines.MSGTYPE_AUDIO

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
            bos.write(audioBlobId)
            bos.writeLittleEndianInt(audioSizeInBytes)
            bos.write(encryptionKey)
            return bos.toByteArray()
        } catch (ioException: IOException) {
            logger.error("Cannot create body of message", ioException)
            return null
        }
    }

    companion object {
        private const val AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
        private const val AUDIO_SIZE_INT_BYTE_LENGTH = 4

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the audio message
         *  @return Instance of [AudioMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): AudioMessage {
            val bodyBytes: ByteArray = message.body.toByteArray()
            val audioMessage = fromByteArray(bodyBytes, 0, bodyBytes.size)
            audioMessage.initializeCommonProperties(message)
            return audioMessage
        }

        /**
         * Build an instance of [AudioMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - audio-duration short (length 4)
         *  - audio-blob-id byte (length 16)
         *  - audio-size int (length 4)
         *  - encryption-key byte (length 32)
         *
         * @param data   the data that represents the audio message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [AudioMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): AudioMessage {
            val minLength =
                AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH + ProtocolDefines.BLOB_ID_LEN + AUDIO_SIZE_INT_BYTE_LENGTH + ProtocolDefines.BLOB_KEY_LEN
            if (length < minLength) {
                throw BadMessageException("Bad length ($length) for audio message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for audio message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            val bis = ByteArrayInputStream(data, offset, length)

            try {
                val durationInSeconds: Short = bis.readLittleEndianShort()

                val audioBlobId = ByteArray(ProtocolDefines.BLOB_ID_LEN)
                bis.read(audioBlobId)

                val audioSizeInBytes: Int = bis.readLittleEndianInt()

                val encryptionKey = ByteArray(ProtocolDefines.BLOB_KEY_LEN)
                bis.read(encryptionKey)

                return AudioMessage(
                    durationInSeconds = durationInSeconds.toInt(),
                    audioBlobId = audioBlobId,
                    audioSizeInBytes = audioSizeInBytes,
                    encryptionKey = encryptionKey,
                )
            } catch (ioException: IOException) {
                throw BadMessageException("Message body contents failed to parse", ioException)
            }
        }
    }
}
