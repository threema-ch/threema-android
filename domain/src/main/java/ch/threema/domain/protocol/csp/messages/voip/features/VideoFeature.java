package ch.threema.domain.protocol.csp.messages.voip.features;

/**
 * Indicate that this client supports video calls.
 */
public class VideoFeature extends SimpleCallFeature {
    public static String NAME = "video";

    public VideoFeature() {
        super(NAME);
    }
}
