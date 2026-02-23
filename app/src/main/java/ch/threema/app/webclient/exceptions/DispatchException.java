package ch.threema.app.webclient.exceptions;

import androidx.annotation.AnyThread;

@AnyThread
public class DispatchException extends Exception {
    public DispatchException(String message) {
        super(message);
    }

    public DispatchException(Throwable throwable) {
        super(throwable);
    }
}
