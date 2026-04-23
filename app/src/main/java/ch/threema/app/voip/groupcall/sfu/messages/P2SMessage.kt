package ch.threema.app.voip.groupcall.sfu.messages

import ch.threema.app.voip.groupcall.sfu.ParticipantId
import ch.threema.base.utils.generateRandomProtobufPadding
import ch.threema.protobuf.common.Resolution
import ch.threema.protobuf.group_call.ParticipantToSfu
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

    data class SubscribeParticipantCamera(
        val participantId: ParticipantId,
        val width: Int,
        val height: Int,
        val fps: Int,
    ) : P2SMessage() {
        override val type = "SubscribeParticipantCamera"

        override fun wrap(envelope: ParticipantToSfu.Envelope.Builder): ParticipantToSfu.Envelope.Builder {
            val resolution = Resolution.newBuilder()
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
