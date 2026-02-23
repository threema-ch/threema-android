package ch.threema.domain.protocol.csp.messages.voip.features;

import org.json.JSONObject;

/**
 * A call feature without parameters.
 */
public class SimpleCallFeature implements CallFeature {
    private final String name;

    public SimpleCallFeature(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public JSONObject getParams() {
        return null;
    }
}
