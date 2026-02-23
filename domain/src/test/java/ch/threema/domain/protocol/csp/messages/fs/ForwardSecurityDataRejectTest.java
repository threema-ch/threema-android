package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Reject;

public class ForwardSecurityDataRejectTest {
    private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
    private static final MessageId TEST_REJECTED_MESSAGE_ID = MessageId.random();
    private static final Reject.Cause TEST_CAUSE = Reject.Cause.UNKNOWN_SESSION;

    private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
        .setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
        .setReject(Reject.newBuilder()
            .setMessageId(TEST_REJECTED_MESSAGE_ID.getMessageIdLong())
            .setGroupIdentity(Common.GroupIdentity.newBuilder().build())
            .setCause(TEST_CAUSE)
            .build())
        .build();

    private static void assertEqualsTestProperties(ForwardSecurityDataReject data) {
        Assertions.assertEquals(TEST_SESSION_ID, data.getSessionId());
        Assertions.assertEquals(TEST_REJECTED_MESSAGE_ID, data.getRejectedApiMessageId());
        Assertions.assertEquals(TEST_CAUSE, data.getCause());
    }

    @Test
    public void testValidData() {
        final ForwardSecurityDataReject data = new ForwardSecurityDataReject(TEST_SESSION_ID, TEST_REJECTED_MESSAGE_ID, null, null, TEST_CAUSE);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final ForwardSecurityDataReject data = (ForwardSecurityDataReject) ForwardSecurityData.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testToProtobufMessage() {
        final ForwardSecurityDataReject data = new ForwardSecurityDataReject(TEST_SESSION_ID, TEST_REJECTED_MESSAGE_ID, null, null, TEST_CAUSE);
        final Envelope generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }
}
