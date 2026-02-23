package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Terminate;

public class ForwardSecurityDataTerminate extends ForwardSecurityData {
    private final @NonNull Terminate.Cause cause;

    public ForwardSecurityDataTerminate(
        @NonNull DHSessionId sessionId,
        @NonNull Terminate.Cause cause
    ) {
        super(sessionId);
        this.cause = cause;
    }

    @NonNull
    public Terminate.Cause getCause() {
        return cause;
    }

    @NonNull
    @Override
    public Envelope toProtobufMessage() {
        return Envelope.newBuilder()
            .setSessionId(ByteString.copyFrom(this.getSessionId().get()))
            .setTerminate(Terminate.newBuilder()
                .setCause(this.cause)
                .build())
            .build();
    }
}
