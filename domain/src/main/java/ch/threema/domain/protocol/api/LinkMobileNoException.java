package ch.threema.domain.protocol.api;

import ch.threema.base.ThreemaException;

/**
 * Exception that may get thrown if there is an error while linking a mobile number to an identity.
 * The message is intended to be displayed to the user and is usually already localized.
 */
public class LinkMobileNoException extends ThreemaException {

    public LinkMobileNoException(String msg) {
        super(msg);
    }
}
