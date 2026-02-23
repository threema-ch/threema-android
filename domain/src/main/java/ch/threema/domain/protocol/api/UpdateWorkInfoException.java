package ch.threema.domain.protocol.api;

import ch.threema.base.ThreemaException;

/**
 * Exception that may get thrown if there is an error while updating the work info of an identity.
 * The message is intended to be displayed to the user and is usually already localized.
 */
public class UpdateWorkInfoException extends ThreemaException {

    public UpdateWorkInfoException(String msg) {
        super(msg);
    }

    public UpdateWorkInfoException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
