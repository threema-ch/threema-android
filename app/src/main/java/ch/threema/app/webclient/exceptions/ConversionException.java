package ch.threema.app.webclient.exceptions;

import androidx.annotation.AnyThread;

@AnyThread
public class ConversionException extends DispatchException {
    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(Throwable throwable) {
        super(throwable);
    }
}
