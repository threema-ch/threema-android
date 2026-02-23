package ch.threema.domain.models;

import java.util.HashMap;
import java.util.Map;

/**
 * The level of trust that a user may have in the validity of the public key for a given contact.
 */
public enum VerificationLevel {
    UNVERIFIED(0), SERVER_VERIFIED(1), FULLY_VERIFIED(2);

    private final int code;
    private static final Map<Integer, VerificationLevel> _map = new HashMap<>();

    VerificationLevel(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    static {
        for (VerificationLevel verificationLevel : VerificationLevel.values())
            _map.put(verificationLevel.code, verificationLevel);
    }

    public static VerificationLevel from(int value) {
        return _map.get(value);
    }
}
