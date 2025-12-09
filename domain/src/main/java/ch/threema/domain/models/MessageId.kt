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

package ch.threema.domain.models

import ch.threema.base.ThreemaException
import ch.threema.common.generateRandomBytes
import ch.threema.common.secureRandom
import ch.threema.common.toByteArray
import ch.threema.common.toLong
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.MessageIdString
import java.io.Serializable
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Wrapper class for message IDs (consisting of [ProtocolDefines.MESSAGE_ID_LEN] bytes, chosen by the sender and not guaranteed
 * to be unique across multiple senders).
 *
 * @throws IllegalArgumentException if the [messageId] is of the wrong length
 */
class MessageId(
    val messageId: ByteArray,
) : Serializable {

    init {
        require(messageId.size == ProtocolDefines.MESSAGE_ID_LEN)
    }

    /**
     * Create a MessageId from an array, starting at the specified [offset].
     *
     * @throws IndexOutOfBoundsException if fewer than [ProtocolDefines.MESSAGE_ID_LEN] bytes are available in [data] at [offset].
     */
    constructor(
        data: ByteArray,
        offset: Int,
    ) : this(data.copyOfRange(offset, offset + ProtocolDefines.MESSAGE_ID_LEN))

    /**
     * Create a MessageId from an (unsigned) long in little-endian format
     */
    constructor(
        messageIdLong: Long,
    ) : this(messageIdLong.toByteArray(order = ByteOrder.LITTLE_ENDIAN))

    val messageIdLong: Long
        get() = messageId.toLong(order = ByteOrder.LITTLE_ENDIAN)

    override fun toString(): MessageIdString = messageId.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageId
        return messageId.contentEquals(other.messageId)
    }

    override fun hashCode(): Int {
        // message IDs are usually random, so just taking the first four bytes is fine
        return (messageId[0].toInt() shl 24) or
            ((messageId[1].toInt() and 0xFF) shl 16) or
            ((messageId[2].toInt() and 0xFF) shl 8) or
            (messageId[3].toInt() and 0xFF)
    }

    companion object {
        /**
         * Create a MessageId from a String
         *
         * @throws ThreemaException If the message id is `null` or has an invalid length
         */
        @JvmStatic
        fun fromString(messageId: MessageIdString?): MessageId {
            if (messageId == null) {
                throw ThreemaException("Message id is null")
            }
            try {
                return MessageId(messageId.hexToByteArray())
            } catch (e: IllegalArgumentException) {
                throw ThreemaException("Message id is invalid", e)
            }
        }

        /**
         * Create a new random MessageId.
         */
        @JvmStatic
        @JvmOverloads
        fun random(random: SecureRandom = secureRandom()): MessageId =
            MessageId(random.generateRandomBytes(ProtocolDefines.MESSAGE_ID_LEN))
    }
}
