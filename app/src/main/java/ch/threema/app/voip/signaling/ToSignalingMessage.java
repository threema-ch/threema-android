package ch.threema.app.voip.signaling;

import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import ch.threema.protobuf.o2o_call.Envelope;

public interface ToSignalingMessage {
    /**
     * Return an ID uniquely identifying the message type.
     * <p>
     * The easiest way to implement this is to return the field number in the protobuf Envelope.
     */
    int getType();

    /**
     * Convert the current type into a voip signaling envelope.
     */
    @NonNull
    Envelope toSignalingMessage();

    /**
     * Convert the current type into voip signaling message bytes
     * (to be sent through the signaling channel).
     */
    default @NonNull byte[] toSignalingMessageBytes() {
        final Envelope envelope = this.toSignalingMessage();
        return envelope.toByteArray();
    }

    /**
     * Convert the current type into voip signaling message bytes
     * (to be sent through the signaling channel).
     */
    default @NonNull ByteBuffer toSignalingMessageByteBuffer() {
        return ByteBuffer.wrap(this.toSignalingMessageBytes());
    }
}
