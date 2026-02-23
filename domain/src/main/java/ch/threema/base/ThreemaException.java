package ch.threema.base;

public class ThreemaException extends Exception {

    private static final long serialVersionUID = -1177013652247088239L;

    public ThreemaException(String msg) {
        super(msg);
    }

    public ThreemaException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
