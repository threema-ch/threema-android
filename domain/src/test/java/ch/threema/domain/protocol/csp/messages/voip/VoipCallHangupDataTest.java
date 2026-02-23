package ch.threema.domain.protocol.csp.messages.voip;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

public class VoipCallHangupDataTest {

    @Test
    public void testValidHangup() throws Exception {
        final VoipCallHangupData msg = new VoipCallHangupData()
            .setCallId(1234);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertEquals("{\"callId\":1234}", json);
    }

    @Test
    public void parseHangupWithCallId() throws BadMessageException {
        final VoipCallHangupData parsed = VoipCallHangupData.parse("{\"callId\":1337}");
        Assertions.assertEquals(Long.valueOf(1337), parsed.getCallId());
    }

    @Test
    public void parseHangupWithoutCallId() throws BadMessageException {
        final VoipCallHangupData parsed = VoipCallHangupData.parse("{}");
        Assertions.assertNull(parsed.getCallId());
    }
}
