package ch.threema.domain.protocol.csp.messages.ballot;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

public class BallotVoteTest {

    static class BallotVoteString extends BallotVote {

        public BallotVoteString(int id, int value) {
            super(id, value);
        }

        @Override
        public String toString() {
            try {
                return this.generateString();
            } catch (BadMessageException e) {
                return "ERROR " + e.getMessage();
            }
        }
    }

    @Test
    public void parseValidString() {
        String correct = "[10,1]";

        try {
            Assertions.assertNotNull(BallotVote.parse(new JSONArray(correct)));
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    public void parseInvalidType() {
        String correct = "[\"a\",\"b\"]";

        try {
            try {
                BallotVote.parse(new JSONArray(correct));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Assertions.fail("wrong type parsed");
        } catch (BadMessageException e) {
            //cool!
        }
    }

    @Test
    public void parseInvalidString() {
        try {
            BallotDataChoice.parse("i want to be a hippie");
            Assertions.fail("invalid string parsed");
        } catch (BadMessageException e) {
            //ok! exception received
        }
    }

    @Test
    public void toStringTest() {
        BallotVote v = new BallotVoteString(100, 1);

        try {
            JSONArray o = new JSONArray("[100, 1]");
            Assertions.assertEquals(o.toString(), v.toString());
        } catch (JSONException e) {
            Assertions.fail("internal error");
        }
    }
}
