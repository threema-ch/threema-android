/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.groupcall

import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.GroupCallStart
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException

class GroupCallStartData(
    val protocolVersion: UInt,
    val gck: ByteArray,
    val sfuBaseUrl: String,
) : ProtobufDataInterface<GroupCallStart> {
    companion object {
        const val GCK_LENGTH = 32

        @JvmStatic
        fun fromProtobuf(rawProtobufMessage: ByteArray): GroupCallStartData {
            try {
                val protobufMessage = GroupCallStart.parseFrom(rawProtobufMessage)
                return GroupCallStartData(
                    protobufMessage.protocolVersion.toUInt(),
                    protobufMessage.gck.toByteArray(),
                    protobufMessage.sfuBaseUrl,
                )
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid group call start protobuf data", e)
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create group call start data", e)
            }
        }
    }

    init {
        if (gck.size != GCK_LENGTH) {
            throw IllegalArgumentException("Invalid length of gck")
        }
    }

    override fun toProtobufMessage(): GroupCallStart {
        return GroupCallStart.newBuilder()
            .setProtocolVersion(protocolVersion.toInt())
            .setGck(ByteString.copyFrom(gck))
            .setSfuBaseUrl(sfuBaseUrl)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupCallStartData) return false

        if (protocolVersion != other.protocolVersion) return false
        if (!gck.contentEquals(other.gck)) return false
        if (sfuBaseUrl != other.sfuBaseUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion.toInt()
        result = 31 * result + gck.contentHashCode()
        result = 31 * result + sfuBaseUrl.hashCode()
        return result
    }

    override fun toString(): String {
        return "GroupCallStartData(protocolVersion=$protocolVersion, gck=******, sfuBaseUrl='$sfuBaseUrl')"
    }
}
