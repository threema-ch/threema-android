package ch.threema.domain.protocol.csp.messages.fs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;

import static ch.threema.domain.testhelpers.TestHelpers.boxMessage;
import static ch.threema.domain.testhelpers.TestHelpers.decodeMessageFromBox;
import static ch.threema.domain.testhelpers.TestHelpers.setMessageDefaultSenderAndReceiver;

public class ForwardSecurityMessageTest {

    private static ForwardSecurityData getDataTestInstance() {
        return new ForwardSecurityDataMessageTest().makeForwardSecurityDataMessage();
    }

    private static ForwardSecurityEnvelopeMessage getEnvelopeMessageTestInstance() {
        final ForwardSecurityEnvelopeMessage msg = new ForwardSecurityEnvelopeMessage(getDataTestInstance(), true);
        setMessageDefaultSenderAndReceiver(msg);
        return msg;
    }

    @Test
    public void makeBoxTest() throws ThreemaException {
        final MessageBox boxedMessage = boxMessage(getEnvelopeMessageTestInstance());
        Assertions.assertNotNull(boxedMessage);
    }

    @Test
    public void decodeFromBoxTest() throws ThreemaException, BadMessageException, MissingPublicKeyException {
        final MessageBox boxedMessage = boxMessage(getEnvelopeMessageTestInstance());

        final AbstractMessage decodedMessage = decodeMessageFromBox(boxedMessage);
        Assertions.assertInstanceOf(ForwardSecurityEnvelopeMessage.class, decodedMessage);
        final ForwardSecurityEnvelopeMessage msg = (ForwardSecurityEnvelopeMessage) decodedMessage;
        Assertions.assertArrayEquals(msg.getData().toProtobufBytes(), getDataTestInstance().toProtobufBytes());
    }
}
