package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.common.GroupIdentity;
import ch.threema.protobuf.csp.e2e.fs.Encapsulated;
import ch.threema.protobuf.csp.e2e.fs.Envelope;

public class ForwardSecurityDataMessage extends ForwardSecurityData {
    private final @NonNull
    Encapsulated.DHType type;
    private final long counter;
    private final int offeredVersion;
    private final int appliedVersion;
    private final @Nullable GroupIdentity groupIdentity;
    private final @NonNull byte[] message;

    public ForwardSecurityDataMessage(
        @NonNull DHSessionId sessionId,
        @NonNull Encapsulated.DHType type,
        long counter,
        int offeredVersion,
        int appliedVersion,
        @Nullable GroupIdentity groupIdentity,
        @NonNull byte[] message
    ) {
        super(sessionId);
        this.type = type;
        this.counter = counter;
        this.offeredVersion = offeredVersion;
        this.appliedVersion = appliedVersion;
        this.groupIdentity = groupIdentity;
        this.message = message;
    }

    @NonNull
    public Encapsulated.DHType getType() {
        return type;
    }

    public long getCounter() {
        return counter;
    }

    public int getOfferedVersion() {
        return offeredVersion;
    }

    public int getAppliedVersion() {
        return appliedVersion;
    }

    @Nullable
    public GroupIdentity getGroupIdentity() {
        return this.groupIdentity;
    }

    @NonNull
    public byte[] getMessage() {
        return message;
    }

    @NonNull
    @Override
    public Envelope toProtobufMessage() {
        // Build the encapsulated message
        Encapsulated.Builder encapsulatedBuilder = Encapsulated.newBuilder()
            .setDhType(type)
            .setCounter(this.counter)
            .setOfferedVersion(this.offeredVersion)
            .setAppliedVersion(this.appliedVersion)
            .setEncryptedInner(ByteString.copyFrom(this.message));

        // Only set group identity if available
        if (groupIdentity != null) {
            encapsulatedBuilder.setGroupIdentity(groupIdentity);
        }

        // Build and return the envelope
        return Envelope.newBuilder()
            .setSessionId(ByteString.copyFrom(this.getSessionId().get()))
            .setEncapsulated(encapsulatedBuilder.build())
            .build();
    }
}
