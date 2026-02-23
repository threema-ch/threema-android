package ch.threema.domain.protocol.csp.messages.ballot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class BallotVote {

    private final static int POS_CHOICE_ID = 0;
    private final static int POS_CHOICE_VALUE = 1;

    private int id;
    private int value;

    public BallotVote(int id, int value) {
        this.id = id;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @NonNull
    public static BallotVote parse(@Nullable JSONArray jsonArray) throws BadMessageException {
        try {
            if (jsonArray == null) {
                throw new BadMessageException("TM036");
            }
            int id = jsonArray.getInt(POS_CHOICE_ID);
            int value = jsonArray.getInt(POS_CHOICE_VALUE);
            return new BallotVote(id, value);
        } catch (JSONException e) {
            throw new BadMessageException("TM033", e);
        }
    }

    public JSONArray getJsonArray() throws BadMessageException {
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray.put(POS_CHOICE_ID, this.id);
            jsonArray.put(POS_CHOICE_VALUE, this.value);
        } catch (Exception e) {
            throw new BadMessageException("TM036", e);
        }
        return jsonArray;
    }

    public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
        bos.write(this.generateString().getBytes(StandardCharsets.US_ASCII));
    }

    public String generateString() throws BadMessageException {
        return this.getJsonArray().toString();
    }
}
