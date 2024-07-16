/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.DeleteMessage
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Objects

class DeleteMessageData(
    val messageId: Long
) : ProtobufDataInterface<DeleteMessage> {

    companion object {
        @JvmStatic
        fun fromProtobuf(rawProtobufMessage: ByteArray): DeleteMessageData {
            try {
                val protobufMessage = DeleteMessage.parseFrom(rawProtobufMessage)
                return DeleteMessageData(
                    protobufMessage.messageId
                )
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid DeleteMessage protobuf data")
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create DeleteMessage data", e)
            }
        }
    }

    override fun toProtobufMessage(): DeleteMessage {
        return DeleteMessage.newBuilder()
            .setMessageId(messageId)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeleteMessageData) return false

        if (messageId != other.messageId) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(messageId)
    }
}
