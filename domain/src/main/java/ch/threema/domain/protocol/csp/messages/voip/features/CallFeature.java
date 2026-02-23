package ch.threema.domain.protocol.csp.messages.voip.features;

import org.json.JSONObject;

/**
 * A call feature.
 */
public interface CallFeature {
    /**
     * Get the name of the feature.
     */
    String getName();

    /**
     * Return the parameters as a JSON object (or null).
     */
    JSONObject getParams();
}
