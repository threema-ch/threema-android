package ch.threema.domain.protocol.csp.messages.voip.features;

import org.json.JSONObject;

/**
 * An unknown call feature.
 */
public class UnknownCallFeature implements CallFeature {
    private final String name;
    private final JSONObject params;

    public UnknownCallFeature(String name, JSONObject params) {
        this.name = name;
        this.params = params;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public JSONObject getParams() {
        return this.params;
    }
}
