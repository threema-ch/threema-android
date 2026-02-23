package ch.threema.domain.protocol.api;

import ch.threema.base.ThreemaException;

/**
 * Exception that may get thrown if there is an error while linking an e-mail address to an identity.
 * The message is intended to be displayed to the user and is usually already localized.
 */
public class LinkEmailException extends ThreemaException {

    public LinkEmailException(String msg) {
        super(msg);
    }

    public LinkEmailException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
