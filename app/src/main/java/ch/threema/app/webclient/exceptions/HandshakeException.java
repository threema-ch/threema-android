package ch.threema.app.webclient.exceptions;

import androidx.annotation.AnyThread;

/**
 * Problems during SaltyRTC handshake.
 */
@AnyThread
public class HandshakeException extends Exception {

    public HandshakeException(String detailMessage) {
        super(detailMessage);
    }

    public HandshakeException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public HandshakeException(Throwable throwable) {
        super(throwable);
    }

}
