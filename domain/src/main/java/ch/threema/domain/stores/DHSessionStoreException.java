package ch.threema.domain.stores;

import ch.threema.base.ThreemaException;

public class DHSessionStoreException extends ThreemaException {
    public DHSessionStoreException(String msg) {
        super(msg);
    }

    public DHSessionStoreException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
