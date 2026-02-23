package ch.threema.domain.protocol.csp.messages.voip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Base class for JSON data in voip messages.
 */
public abstract class VoipCallData<T extends VoipCallData<?>> {
    protected final static String KEY_CALL_ID = "callId";

    private @Nullable Long callId;

    public @Nullable Long getCallId() {
        return this.callId;
    }

    public long getCallIdOrDefault(long defaultValue) {
        return this.callId == null ? defaultValue : this.callId;
    }

    public T setCallId(long callId) throws IllegalArgumentException {
        if (callId < 0) {
            throw new IllegalArgumentException("callId must be positive, but was " + callId);
        }
        if (callId >= (1L << 32)) {
            throw new IllegalArgumentException("callId must fit in an unsigned 32bit integer, but was " + callId);
        }
        this.callId = callId;
        //noinspection unchecked
        return (T) this;
    }

    /**
     * Create a new empty {@link JSONObject} and add common fields (e.g. `callId`)
     * to it.
     */
    protected @NonNull JSONObject buildJsonObject() {
        final JSONObject o = new JSONObject();

        // Add call ID
        if (this.getCallId() != null) {
            try {
                o.put(KEY_CALL_ID, (long) this.getCallId());
            } catch (JSONException e) {
                // Should never happen™
                throw new RuntimeException("Call to JSONObject.put failed", e);
            }
        }

        return o;
    }
}
