package ch.threema.app.webclient;

import androidx.annotation.AnyThread;
import ch.threema.app.utils.TurnServerCache;

/**
 * WebClient configuration.
 */
@AnyThread
public class Config {
    private static final int MIN_SPARE_TURN_VALIDITY = 6 * 3600 * 1000;

    private static final TurnServerCache TURN_SERVER_CACHE = new TurnServerCache("web", MIN_SPARE_TURN_VALIDITY);

    public static TurnServerCache getTurnServerCache() {
        return TURN_SERVER_CACHE;
    }

    private Config() {
        // This class only contains static fields and should not be instantiated
    }
}
