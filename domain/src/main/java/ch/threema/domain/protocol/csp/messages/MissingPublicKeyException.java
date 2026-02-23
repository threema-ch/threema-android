package ch.threema.domain.protocol.csp.messages;

/**
 * Exception thrown when a required public key is missing, e.g. for an encryption operation.
 */
public class MissingPublicKeyException extends Exception {

    public MissingPublicKeyException(String msg) {
        super(msg);
    }

    public MissingPublicKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
