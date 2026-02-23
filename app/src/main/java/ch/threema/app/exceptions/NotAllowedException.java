package ch.threema.app.exceptions;

import ch.threema.base.ThreemaException;

public class NotAllowedException extends ThreemaException {
    public NotAllowedException() {
        super("action not allowed");
    }

    public NotAllowedException(String s) {
        super(s);
    }
}
