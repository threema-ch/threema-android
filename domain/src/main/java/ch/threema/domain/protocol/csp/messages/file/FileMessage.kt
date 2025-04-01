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

package ch.threema.domain.protocol.csp.messages.file

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.MdD2D
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger: Logger = LoggingUtil.getThreemaLogger("FileMessage")

/**
 *  This class is not final as a workaround to test it using Mockito.
 *  Another solution would be to use the mock-maker-inline mockito plugin.
 */
open class FileMessage : AbstractMessage(), FileMessageInterface {

    override var fileData: FileData? = null

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
        return try {
            val bos = ByteArrayOutputStream()
            fileData!!.write(bos)
            bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_FILE

    companion object {

        /**
         *  When the message bytes come from sync (reflected), they do not contain the one extra byte at the beginning.
         *  So we set the offset in [fromByteArray] to zero.
         *
         *  In addition the common message model properties ([fromIdentity], [messageId] and [date]) get set.
         *
         *  @param message the MdD2D message representing the file message
         *  @return Instance of [FileMessage]
         *  @see fromByteArray
         */
        @JvmStatic
        fun fromReflected(message: MdD2D.IncomingMessage): FileMessage {
            val fileMessage = fromByteArray(message.body.toByteArray())
            fileMessage.initializeCommonProperties(message)
            return fileMessage
        }

        @JvmStatic
        fun fromByteArray(data: ByteArray): FileMessage = fromByteArray(data, 0, data.size)

        /**
         * Build an instance of [FileMessage] from the given [data] bytes. Note that
         * the common message model properties ([fromIdentity], [messageId] and [date]) will **not** be set.
         *
         * The [data] byte array consists of:
         *  - body json bytes of [FileData]
         *
         * @param data   the data that represents the file message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [FileMessage]
         * @throws BadMessageException if the length or the offset is invalid
         * @see fromReflected
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): FileMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for file message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for file message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }
            return FileMessage().apply {
                val json = String(data, offset, length, StandardCharsets.UTF_8)
                fileData = FileData.parse(json)
            }
        }
    }
}
