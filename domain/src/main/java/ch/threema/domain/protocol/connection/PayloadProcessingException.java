package ch.threema.domain.protocol.connection;

public class PayloadProcessingException extends Exception {

    private static final long serialVersionUID = -2619972211818695496L;

    public PayloadProcessingException(String msg) {
        super(msg);
    }
}
