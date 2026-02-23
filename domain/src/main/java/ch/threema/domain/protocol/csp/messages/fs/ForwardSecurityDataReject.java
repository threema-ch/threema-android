package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Reject;

public class ForwardSecurityDataReject extends ForwardSecurityData {
    private final @NonNull MessageId rejectedMessageId;
    private final @Nullable Common.GroupIdentity groupIdentity;
    private final @NonNull Reject.Cause cause;

    public ForwardSecurityDataReject(
        @NonNull DHSessionId sessionId,
        @NonNull MessageId rejectedMessageId,
        @Nullable Common.GroupIdentity groupIdentity,
        @NonNull Reject.Cause cause
    ) {
        this(
            sessionId,
            rejectedMessageId,
            groupIdentity != null ? groupIdentity.getGroupId() : null,
            groupIdentity != null ? groupIdentity.getCreatorIdentity() : null,
            cause
        );
    }

    public ForwardSecurityDataReject(
        @NonNull DHSessionId sessionId,
        @NonNull MessageId rejectedMessageId,
        @Nullable Long groupId,
        @Nullable String groupCreator,
        @NonNull Reject.Cause cause
    ) {
        super(sessionId);
        this.rejectedMessageId = rejectedMessageId;
        this.cause = cause;

        if (groupId != null && groupCreator != null) {
            this.groupIdentity = Common.GroupIdentity.newBuilder()
                .setGroupId(groupId)
                .setCreatorIdentity(groupCreator)
                .build();
        } else {
            this.groupIdentity = Common.GroupIdentity.newBuilder().build();
        }
    }

    @NonNull
    public MessageId getRejectedApiMessageId() {
        return rejectedMessageId;
    }

    @Nullable
    public Common.GroupIdentity getGroupIdentity() {
        return groupIdentity;
    }

    @NonNull
    public Reject.Cause getCause() {
        return cause;
    }

    @NonNull
    @Override
    public Envelope toProtobufMessage() {
        return Envelope.newBuilder()
            .setSessionId(ByteString.copyFrom(this.getSessionId().get()))
            .setReject(Reject.newBuilder()
                .setMessageId(this.rejectedMessageId.getMessageIdLong())
                .setGroupIdentity(this.groupIdentity)
                .setCause(this.cause)
                .build())
            .build();
    }
}
