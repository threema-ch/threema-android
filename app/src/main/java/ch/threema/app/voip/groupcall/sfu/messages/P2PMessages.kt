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

package ch.threema.app.voip.groupcall.sfu.messages

import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.sfu.ParticipantId
import ch.threema.app.voip.groupcall.sfu.SfuException
import ch.threema.app.voip.groupcall.sfu.webrtc.ParticipantCallMediaKeyState
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.SecureRandomUtil.generateRandomProtobufPadding
import ch.threema.domain.types.Identity
import ch.threema.protobuf.Common
import ch.threema.protobuf.groupcall.ParticipantToParticipant
import ch.threema.protobuf.groupcall.ParticipantToSfu
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("P2PMessages")

sealed interface Handshake {
    fun getEnvelopeBytes(): ByteArray

    data class Hello(
        val identity: Identity,
        val nickname: String,
        val pck: ByteArray,
        val pcck: ByteArray,
    ) : Handshake {
        companion object {
            fun decode(bytes: ByteArray): Hello {
                val envelope = ParticipantToParticipant.Handshake.HelloEnvelope.parseFrom(bytes)
                if (!envelope.hasHello()) {
                    throw GroupCallException("HelloEnvelope does not contain a Hello message")
                }
                val hello = envelope.hello
                return Hello(
                    hello.identity,
                    hello.nickname,
                    hello.pck.toByteArray(),
                    hello.pcck.toByteArray(),
                )
            }
        }

        override fun getEnvelopeBytes(): ByteArray {
            val hello = ParticipantToParticipant.Handshake.Hello.newBuilder()
                .setIdentity(identity)
                .setNickname(nickname)
                .setPck(ByteString.copyFrom(pck))
                .setPcck(ByteString.copyFrom(pcck))
                .build()
            return ParticipantToParticipant.Handshake.HelloEnvelope.newBuilder()
                .setHello(hello)
                .setPadding(generateRandomProtobufPadding())
                .build()
                .toByteArray()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Hello) return false

            if (identity != other.identity) return false
            if (nickname != other.nickname) return false
            if (!pck.contentEquals(other.pck)) return false
            if (!pcck.contentEquals(other.pcck)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = identity.hashCode()
            result = 31 * result + nickname.hashCode()
            result = 31 * result + pck.contentHashCode()
            result = 31 * result + pcck.contentHashCode()
            return result
        }
    }

    data class Auth(
        val pck: ByteArray,
        val pcck: ByteArray,
        val mediaKeys: List<P2PMessageContent.MediaKey>,
    ) : Handshake {
        companion object {
            fun decode(bytes: ByteArray): Auth {
                val envelope = ParticipantToParticipant.Handshake.AuthEnvelope.parseFrom(bytes)
                if (!envelope.hasAuth()) {
                    throw GroupCallException("AuthEnvelop does not contain an Auth message")
                }
                val auth = envelope.auth
                return Auth(
                    auth.pck.toByteArray(),
                    auth.pcck.toByteArray(),
                    auth.mediaKeysList.map { P2PMessageContent.MediaKey.fromProtobuf(it) },
                )
            }
        }

        override fun getEnvelopeBytes(): ByteArray {
            val protoMediaKeys = mediaKeys.map {
                ParticipantToParticipant.MediaKey.newBuilder()
                    .setEpoch(it.epoch.toInt())
                    .setRatchetCounter(it.ratchetCounter.toInt())
                    .setPcmk(ByteString.copyFrom(it.pcmk))
                    .build()
            }

            val auth = ParticipantToParticipant.Handshake.Auth.newBuilder()
                .setPck(ByteString.copyFrom(pck))
                .setPcck(ByteString.copyFrom(pcck))
                .addAllMediaKeys(protoMediaKeys)
                .build()

            return ParticipantToParticipant.Handshake.AuthEnvelope.newBuilder()
                .setAuth(auth)
                .setPadding(generateRandomProtobufPadding())
                .build()
                .toByteArray()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Auth) return false

            if (!pck.contentEquals(other.pck)) return false
            if (!pcck.contentEquals(other.pcck)) return false
            if (mediaKeys != other.mediaKeys) return false

            return true
        }

        override fun hashCode(): Int {
            var result = pck.contentHashCode()
            result = 31 * result + pcck.contentHashCode()
            result = 31 * result + mediaKeys.hashCode()
            return result
        }
    }
}

sealed class P2PMessageContent {
    abstract val type: String

    fun toProtobufEnvelope(): ParticipantToParticipant.Envelope {
        return wrap(ParticipantToParticipant.Envelope.newBuilder())
            .setPadding(generateRandomProtobufPadding())
            .build()
    }

    abstract fun wrap(envelope: ParticipantToParticipant.Envelope.Builder): ParticipantToParticipant.Envelope.Builder

    sealed class CaptureState : P2PMessageContent() {
        companion object {
            fun fromProtobuf(captureState: ParticipantToParticipant.CaptureState): CaptureState? {
                return when (captureState.stateCase) {
                    ParticipantToParticipant.CaptureState.StateCase.MICROPHONE -> Microphone(captureState.microphone.hasOn())
                    ParticipantToParticipant.CaptureState.StateCase.CAMERA -> Camera(captureState.camera.hasOn())
                    ParticipantToParticipant.CaptureState.StateCase.SCREEN -> if (captureState.screen.hasOn()) {
                        Screen.on(Date(captureState.screen.on.startedAt))
                    } else {
                        Screen.off()
                    }
                    ParticipantToParticipant.CaptureState.StateCase.STATE_NOT_SET -> null
                    null -> {
                        logger.warn("Capture state is not set")
                        return null
                    }
                }
            }
        }

        abstract val active: Boolean

        protected abstract fun toProtobuf(): ParticipantToParticipant.CaptureState

        override fun wrap(envelope: ParticipantToParticipant.Envelope.Builder): ParticipantToParticipant.Envelope.Builder {
            return envelope.setCaptureState(toProtobuf())
        }

        data class Microphone(override val active: Boolean) : CaptureState() {
            override val type = "CaptureState.Microphone"

            override fun toProtobuf(): ParticipantToParticipant.CaptureState {
                val builder = ParticipantToParticipant.CaptureState.Microphone.newBuilder()
                val unit = Common.Unit.newBuilder().build()
                if (active) {
                    builder.on = unit
                } else {
                    builder.off = unit
                }
                return ParticipantToParticipant.CaptureState.newBuilder()
                    .setMicrophone(builder.build())
                    .build()
            }
        }

        data class Camera(override val active: Boolean) : CaptureState() {
            override val type = "CaptureState.Camera"

            override fun toProtobuf(): ParticipantToParticipant.CaptureState {
                val builder = ParticipantToParticipant.CaptureState.Camera.newBuilder()
                val unit = Common.Unit.newBuilder().build()
                if (active) {
                    builder.on = unit
                } else {
                    builder.off = unit
                }
                return ParticipantToParticipant.CaptureState.newBuilder()
                    .setCamera(builder.build())
                    .build()
            }
        }

        /**
         * Capture state for screensharing. If [startedAt] is provided, this means that screen sharing is active.
         */
        data class Screen(val startedAt: Date?) : CaptureState() {
            override val type = "CaptureState.Screen"

            override val active: Boolean
                get() = startedAt != null

            override fun toProtobuf(): ParticipantToParticipant.CaptureState {
                val builder = ParticipantToParticipant.CaptureState.Screen.newBuilder()
                if (startedAt != null) {
                    builder.on = ParticipantToParticipant.CaptureState.Screen.On.newBuilder()
                        .setStartedAt(startedAt.time)
                        .build()
                } else {
                    builder.off = Common.Unit.newBuilder().build()
                }
                return ParticipantToParticipant.CaptureState.newBuilder()
                    .setScreen(builder.build())
                    .build()
            }

            companion object {
                fun on(startedAt: Date) = Screen(startedAt)
                fun off() = Screen(null)
            }
        }
    }

    data class MediaKey(val epoch: UInt, val ratchetCounter: UInt, val pcmk: ByteArray) :
        P2PMessageContent() {
        override val type = "MediaKey"

        companion object {
            fun fromState(state: ParticipantCallMediaKeyState): MediaKey {
                return MediaKey(
                    state.epoch,
                    state.ratchetCounter,
                    state.pcmk,
                )
            }

            fun fromProtobuf(mediaKey: ParticipantToParticipant.MediaKey): MediaKey {
                return MediaKey(
                    mediaKey.epoch.toUInt(),
                    mediaKey.ratchetCounter.toUInt(),
                    mediaKey.pcmk.toByteArray(),
                )
            }
        }

        override fun wrap(envelope: ParticipantToParticipant.Envelope.Builder): ParticipantToParticipant.Envelope.Builder {
            val mediaKey = ParticipantToParticipant.MediaKey.newBuilder()
                .setEpoch(epoch.toInt())
                .setRatchetCounter(ratchetCounter.toInt())
                .setPcmk(ByteString.copyFrom(pcmk))
                .build()

            return envelope.setRekey(mediaKey)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MediaKey) return false

            if (epoch != other.epoch) return false
            if (ratchetCounter != other.ratchetCounter) return false
            if (!pcmk.contentEquals(other.pcmk)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = epoch.hashCode()
            result = 31 * result + ratchetCounter.hashCode()
            result = 31 * result + pcmk.contentHashCode()
            return result
        }
    }
}

data class P2POuterEnvelope(
    val senderId: ParticipantId,
    val receiverId: ParticipantId,
    val encryptedData: ByteArray,
) : S2PMessage, P2SMessage() {
    override val type = "P2POuterEnvelope"

    companion object {
        fun fromProtobuf(outerEnvelope: ParticipantToParticipant.OuterEnvelope): P2POuterEnvelope {
            try {
                return P2POuterEnvelope(
                    ParticipantId(outerEnvelope.sender.toUInt()),
                    ParticipantId(outerEnvelope.receiver.toUInt()),
                    outerEnvelope.encryptedData.toByteArray(),
                )
            } catch (e: InvalidProtocolBufferException) {
                throw SfuException("Failed to decode P2PMessage", e)
            }
        }
    }

    override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
        val outerEnvelope = toOuterEnvelope()
        return envelope.setRelay(outerEnvelope)
    }

    private fun toOuterEnvelope(): ParticipantToParticipant.OuterEnvelope {
        return ParticipantToParticipant.OuterEnvelope.newBuilder()
            .setSender(senderId.id.toInt())
            .setReceiver(receiverId.id.toInt())
            .setEncryptedData(ByteString.copyFrom(encryptedData))
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is P2POuterEnvelope) return false

        if (senderId != other.senderId) return false
        if (receiverId != other.receiverId) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + receiverId.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        return result
    }
}
