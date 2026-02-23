package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;
import ch.threema.base.crypto.NaCl;

import androidx.annotation.NonNull;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.csp.e2e.fs.Accept;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;

public class ForwardSecurityDataAccept extends ForwardSecurityData {
    private final @NonNull VersionRange versionRange;
    private final @NonNull byte[] ephemeralPublicKey;

    public ForwardSecurityDataAccept(
        @NonNull DHSessionId sessionId,
        @NonNull VersionRange versionRange,
        @NonNull byte[] ephemeralPublicKey
    ) throws InvalidEphemeralPublicKeyException {
        super(sessionId);
        this.versionRange = versionRange;
        if (ephemeralPublicKey.length != NaCl.PUBLIC_KEY_BYTES) {
            throw new InvalidEphemeralPublicKeyException("Bad ephemeral public key length");
        }
        this.ephemeralPublicKey = ephemeralPublicKey;
    }

    @NonNull
    public VersionRange getVersionRange() {
        return versionRange;
    }

    @NonNull
    public byte[] getEphemeralPublicKey() {
        return ephemeralPublicKey;
    }

    @NonNull
    @Override
    public Envelope toProtobufMessage() {
        return Envelope.newBuilder()
            .setSessionId(ByteString.copyFrom(this.getSessionId().get()))
            .setAccept(Accept.newBuilder()
                .setSupportedVersion(this.versionRange)
                .setFssk(ByteString.copyFrom(this.ephemeralPublicKey))
                .build())
            .build();
    }
}
