package ch.threema.domain.protocol.csp.messages;

public class BadMessageException extends Exception {

    public BadMessageException(String msg) {
        super(msg);
    }

    public BadMessageException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
