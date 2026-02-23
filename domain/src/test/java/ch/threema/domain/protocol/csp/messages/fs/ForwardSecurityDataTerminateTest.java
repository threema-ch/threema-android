package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Terminate;

public class ForwardSecurityDataTerminateTest {
    private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
    private static final Terminate.Cause TEST_CAUSE = Terminate.Cause.UNKNOWN_SESSION;

    private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
        .setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
        .setTerminate(Terminate.newBuilder()
            .build())
        .build();

    private static void assertEqualsTestProperties(ForwardSecurityDataTerminate data) {
        Assertions.assertEquals(TEST_SESSION_ID, data.getSessionId());
    }

    @Test
    public void testValidData() {
        final ForwardSecurityDataTerminate data = new ForwardSecurityDataTerminate(TEST_SESSION_ID, TEST_CAUSE);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final ForwardSecurityDataTerminate data = (ForwardSecurityDataTerminate) ForwardSecurityData.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testToProtobufMessage() {
        final ForwardSecurityDataTerminate data = new ForwardSecurityDataTerminate(TEST_SESSION_ID, TEST_CAUSE);
        final Envelope generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }
}
