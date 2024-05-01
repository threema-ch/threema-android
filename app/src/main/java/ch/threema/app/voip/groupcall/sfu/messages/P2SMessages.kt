/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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
import ch.threema.base.utils.SecureRandomUtil.generateRandomProtobufPadding
import ch.threema.protobuf.Common
import ch.threema.protobuf.groupcall.ParticipantToSfu
import com.google.protobuf.ByteString

sealed class P2SMessage {
    abstract val type: String

    fun toProtobufEnvelope(): ParticipantToSfu.Envelope {
        val builder = wrap(ParticipantToSfu.Envelope.newBuilder())

        // Omit padding for p2p messages as these are already padded
        if (this !is P2POuterEnvelope) {
            builder.padding = generateRandomProtobufPadding()
        }

        return builder.build()
    }

    protected abstract fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder

    data class UpdateCallState(val encryptedCallState: ByteArray) : P2SMessage() {
        override val type = "UpdateCallState"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val update = ParticipantToSfu.UpdateCallState.newBuilder()
                .setEncryptedCallState(ByteString.copyFrom(encryptedCallState))
                .build()

            return envelope.setUpdateCallState(update)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UpdateCallState) return false

            if (!encryptedCallState.contentEquals(other.encryptedCallState)) return false

            return true
        }

        override fun hashCode(): Int {
            return encryptedCallState.contentHashCode()
        }
    }

    data class SubscribeParticipantMicrophone(val participantId: ParticipantId) : P2SMessage() {
        override val type = "SubscribeParticipantMicrophone"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val subscribe = ParticipantToSfu.ParticipantMicrophone.Subscribe
                .newBuilder()
                .build()

            val participantMicrophone = ParticipantToSfu.ParticipantMicrophone.newBuilder()
                .setParticipantId(participantId.id.toInt())
                .setSubscribe(subscribe)
                .build()

            return envelope.setRequestParticipantMicrophone(participantMicrophone)
        }
    }

    data class UnsubscribeParticipantMicrophone(val participantId: ParticipantId) : P2SMessage() {
        override val type = "UnsubscribeParticipantMicrophone"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val unsubscribe = ParticipantToSfu.ParticipantMicrophone.Unsubscribe
                .newBuilder()
                .build()

            val participantMicrophone = ParticipantToSfu.ParticipantMicrophone.newBuilder()
                .setParticipantId(participantId.id.toInt())
                .setUnsubscribe(unsubscribe)
                .build()

            return envelope.setRequestParticipantMicrophone(participantMicrophone)
        }
    }

    data class SubscribeParticipantCamera(val participantId: ParticipantId, val width: Int, val height: Int, val fps: Int) : P2SMessage() {
        override val type = "SubscribeParticipantCamera"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val resolution = Common.Resolution.newBuilder()
                .setWidth(width)
                .setHeight(height)
                .build()

            val subscribe = ParticipantToSfu.ParticipantCamera.Subscribe.newBuilder()
                .setDesiredResolution(resolution)
                .setDesiredFps(fps)
                .build()

            val participantCamera = ParticipantToSfu.ParticipantCamera.newBuilder()
                .setParticipantId(participantId.id.toInt())
                .setSubscribe(subscribe)
                .build()

            return envelope.setRequestParticipantCamera(participantCamera)
        }
    }

    data class UnsubscribeParticipantCamera(val participantId: ParticipantId) : P2SMessage() {
        override val type = "UnsubscribeParticipantCamera"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val unsubscribe = ParticipantToSfu.ParticipantCamera.Unsubscribe
                .newBuilder()
                .build()

            val participantCamera = ParticipantToSfu.ParticipantCamera.newBuilder()
                .setParticipantId(participantId.id.toInt())
                .setUnsubscribe(unsubscribe)
                .build()

            return envelope.setRequestParticipantCamera(participantCamera)
        }
    }
}
