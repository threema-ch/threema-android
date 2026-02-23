package ch.threema.domain.protocol.csp.messages.voip;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData.Candidate;

public class VoipICECandidatesDataDataTest {

    /**
     * A valid candidate.
     */
    @Test
    void testSerializeValidCandidate() throws Exception {
        final Candidate candidate1 = new Candidate("foo", "bar", 3, "baz");
        //noinspection ConstantConditions
        final Candidate candidate2 = new Candidate("a", null, null, null);

        // Note: Order of fields depends on the JSON impl :(
        final String json1 = candidate1.toJSON().toString();
        Assertions.assertTrue(json1.equals("{\"candidate\":\"foo\",\"sdpMid\":\"bar\",\"sdpMLineIndex\":3,\"ufrag\":\"baz\"}") ||
            json1.equals("{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"}"), json1);

        final String json2 = candidate2.toJSON().toString();
        Assertions.assertTrue(json2.equals("{\"candidate\":\"a\",\"sdpMid\":null,\"sdpMLineIndex\":null,\"ufrag\":null}") ||
            json2.equals("{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}"), json2);
    }

    /**
     * Serialize a valid candidates message.
     */
    @Test
    void testSerializeValidCandidates() throws Exception {
        final Candidate candidate1 = new Candidate("foo", "bar", 3, "baz");
        //noinspection ConstantConditions
        final Candidate candidate2 = new Candidate("a", null, null, null);

        final VoipICECandidatesData msg = new VoipICECandidatesData()
            .setCandidates(new Candidate[]{candidate1, candidate2});

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"removed\":false"), json);
        Assertions.assertTrue(json.contains("\"candidates\":["), json);
        Assertions.assertTrue(json.contains("{\"candidate\":\"foo\",\"sdpMid\":\"bar\",\"sdpMLineIndex\":3,\"ufrag\":\"baz\"}") ||
            json.contains("{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"}"), json);
        Assertions.assertTrue(json.contains("{\"candidate\":\"a\",\"sdpMid\":null,\"sdpMLineIndex\":null,\"ufrag\":null}") ||
            json.contains("{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}"), json);
    }

    /**
     * Parse a valid candidates message.
     */
    @Test
    void testParseValidCandidates() throws Exception {
        final VoipICECandidatesData parsed = VoipICECandidatesData.parse("{\"candidates\":[{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"},{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}],\"removed\":true}");

        Assertions.assertTrue(parsed.isRemoved());
        Assertions.assertNotNull(parsed.getCandidates());
        Assertions.assertEquals(2, parsed.getCandidates().length);

        final Candidate candidate1 = parsed.getCandidates()[0];
        Assertions.assertEquals("foo", candidate1.getCandidate());
        Assertions.assertEquals("bar", candidate1.getSdpMid());
        Assertions.assertEquals(Integer.valueOf(3), candidate1.getSdpMLineIndex());
        Assertions.assertEquals("baz", candidate1.getUfrag());

        final Candidate candidate2 = parsed.getCandidates()[1];
        Assertions.assertEquals("a", candidate2.getCandidate());
        Assertions.assertNull(candidate2.getSdpMid());
        Assertions.assertNull(candidate2.getSdpMLineIndex());
        Assertions.assertNull(candidate2.getUfrag());
    }

    /**
     * Candidates message without candidates
     */
    @Test
    void parseNoCandidates() {
        try {
            VoipICECandidatesData.parse("{\"removed\":true}");
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Candidates message with null candidates list
     */
    @Test
    void parseNullCandidatesList() {
        try {
            VoipICECandidatesData.parse("{\"removed\":true,\"candidates\":null}");
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Candidates message with null candidates
     */
    @Test
    void parseNullCandidates() {
        try {
            VoipICECandidatesData.parse("{\"removed\":true,\"candidates\":[null]}");
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

}
