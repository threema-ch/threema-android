package ch.threema.domain.protocol.csp.messages.fs;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Init;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;

public class ForwardSecurityDataInitTest {
    private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
    private static final byte[] TEST_EPHEMERAL_PUBLIC_KEY = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
    private static final VersionRange SUPPORTED_VERSION_RANGE = VersionRange.getDefaultInstance();

    private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
        .setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
        .setInit(Init.newBuilder()
            .setFssk(ByteString.copyFrom(TEST_EPHEMERAL_PUBLIC_KEY))
            .setSupportedVersion(SUPPORTED_VERSION_RANGE)
            .build())
        .build();

    private static void assertEqualsTestProperties(ForwardSecurityDataInit data) {
        Assertions.assertEquals(TEST_SESSION_ID, data.getSessionId());
        Assertions.assertArrayEquals(TEST_EPHEMERAL_PUBLIC_KEY, data.getEphemeralPublicKey());
    }

    @Test
    public void testValidData() throws ForwardSecurityData.InvalidEphemeralPublicKeyException {
        final ForwardSecurityDataInit data = new ForwardSecurityDataInit(TEST_SESSION_ID, SUPPORTED_VERSION_RANGE, TEST_EPHEMERAL_PUBLIC_KEY);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final ForwardSecurityDataInit data = (ForwardSecurityDataInit) ForwardSecurityData.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testToProtobufMessage() throws ForwardSecurityData.InvalidEphemeralPublicKeyException {
        final ForwardSecurityDataInit data = new ForwardSecurityDataInit(TEST_SESSION_ID, SUPPORTED_VERSION_RANGE, TEST_EPHEMERAL_PUBLIC_KEY);
        final Envelope generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }
}
