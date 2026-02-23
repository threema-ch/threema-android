package ch.threema.app.exceptions;

import ch.threema.base.ThreemaException;

public class NoIdentityException extends ThreemaException {
    public NoIdentityException() {
        super("no identity found");
    }
}
