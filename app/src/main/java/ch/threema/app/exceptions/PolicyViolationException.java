package ch.threema.app.exceptions;

/**
 * Exception to be thrown when app restriction policy is violated
 */
public class PolicyViolationException extends Exception {
    public PolicyViolationException() {
        super("Disabled by policy");
    }

    public PolicyViolationException(String msg) {
        super(msg);
    }

    public PolicyViolationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
