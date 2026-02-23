package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.csp.e2e.fs.Encapsulated;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class ForwardSecurityDataMessageTest {
    private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
    private static final Encapsulated.DHType TEST_DH_TYPE = Encapsulated.DHType.FOURDH;
    private static final long TEST_COUNTER = 1;
    private static final byte[] TEST_MESSAGE = new byte[]{0x01, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f};

    private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
        .setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
        .setEncapsulated(Encapsulated.newBuilder()
            .setDhType(TEST_DH_TYPE)
            .setCounter(TEST_COUNTER)
            .setEncryptedInner(ByteString.copyFrom(TEST_MESSAGE))
            .setOfferedVersion(Version.V1_1_VALUE)
            .setAppliedVersion(Version.V1_0_VALUE)
            .build())
        .build();

    private static void assertEqualsTestProperties(ForwardSecurityDataMessage data) {
        Assertions.assertEquals(TEST_SESSION_ID, data.getSessionId());
        Assertions.assertEquals(TEST_DH_TYPE, data.getType());
        Assertions.assertEquals(TEST_COUNTER, data.getCounter());
        Assertions.assertEquals(Version.V1_1_VALUE, data.getOfferedVersion());
        Assertions.assertEquals(Version.V1_0_VALUE, data.getAppliedVersion());
        Assertions.assertArrayEquals(TEST_MESSAGE, data.getMessage());
    }

    public ForwardSecurityDataMessage makeForwardSecurityDataMessage() {
        return new ForwardSecurityDataMessage(
            TEST_SESSION_ID, Encapsulated.DHType.FOURDH, TEST_COUNTER, Version.V1_1_VALUE, Version.V1_0_VALUE, null, TEST_MESSAGE);
    }

    @Test
    public void testValidData() {
        final ForwardSecurityDataMessage data = makeForwardSecurityDataMessage();
        assertEqualsTestProperties(data);
    }

    @Test
    public void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) ForwardSecurityDataMessage.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testToProtobufMessage() {
        final ForwardSecurityDataMessage data = makeForwardSecurityDataMessage();
        final Envelope generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }
}
