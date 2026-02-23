package ch.threema.domain.protocol.csp.messages.fs;

import ch.threema.base.ThreemaException;

public class InvalidEphemeralPublicKeyException extends ThreemaException {
    public InvalidEphemeralPublicKeyException(final String msg) {
        super(msg);
    }
}
