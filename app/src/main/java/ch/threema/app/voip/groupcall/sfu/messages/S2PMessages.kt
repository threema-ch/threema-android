/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.messages

import ch.threema.app.voip.groupcall.sfu.ParticipantId
import ch.threema.app.voip.groupcall.sfu.SfuException
import ch.threema.protobuf.groupcall.SfuToParticipant
import org.webrtc.DataChannel

sealed interface S2PMessage {
    companion object {
        fun decode(byteBuffer: DataChannel.Buffer): S2PMessage {
            // Protobuf fails when parsing the buffer directly, so we get the bytes ourselves.
            val bytes = ByteArray(byteBuffer.data.remaining())
            byteBuffer.data.get(bytes)
            val envelope = SfuToParticipant.Envelope.parseFrom(bytes)
            return when {
                envelope.hasRelay() -> P2POuterEnvelope.fromProtobuf(envelope.relay)
                envelope.hasHello() -> SfuHello.fromProtobuf(envelope.hello)
                envelope.hasParticipantJoined() -> SfuParticipantJoined.fromProtobuf(envelope.participantJoined)
                envelope.hasParticipantLeft() -> SfuParticipantLeft.fromProtobuf(envelope.participantLeft)
                else -> throw SfuException("Unknown Sfu2Participant Message")
            }
        }
    }

    data class SfuHello(val participantIds: Set<ParticipantId>) : S2PMessage {
        companion object {
            internal fun fromProtobuf(hello: SfuToParticipant.Hello): SfuHello {
                val participants = hello.participantIdsList
                    .map { ParticipantId(it.toUInt()) }
                    .toSet()
                return SfuHello(participants)
            }
        }
    }

    data class SfuParticipantJoined(val participantId: ParticipantId) : S2PMessage {
        companion object {
            internal fun fromProtobuf(joined: SfuToParticipant.ParticipantJoined): SfuParticipantJoined {
                return SfuParticipantJoined(ParticipantId(joined.participantId.toUInt()))
            }
        }
    }

    data class SfuParticipantLeft(val participantId: ParticipantId) : S2PMessage {
        companion object {
            internal fun fromProtobuf(left: SfuToParticipant.ParticipantLeft): SfuParticipantLeft {
                return SfuParticipantLeft(ParticipantId(left.participantId.toUInt()))
            }
        }
    }
}
