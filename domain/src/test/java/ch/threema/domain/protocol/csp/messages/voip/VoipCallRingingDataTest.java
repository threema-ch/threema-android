package ch.threema.domain.protocol.csp.messages.voip;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

public class VoipCallRingingDataTest {

    @Test
    public void testValidRinging() throws Exception {
        final VoipCallRingingData msg = new VoipCallRingingData()
            .setCallId(1234);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertEquals("{\"callId\":1234}", json);
    }

    @Test
    public void parseRingingWithCallId() throws BadMessageException {
        final VoipCallRingingData parsed = VoipCallRingingData.parse("{\"callId\":42}");
        Assertions.assertEquals(Long.valueOf(42), parsed.getCallId());
    }

    @Test
    public void parseRingingWithoutCallId() throws BadMessageException {
        final VoipCallRingingData parsed = VoipCallRingingData.parse("{}");
        Assertions.assertNull(parsed.getCallId());
    }
}
