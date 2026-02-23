package ch.threema.app.video.transcoder;

public class UnrecoverableVideoTranscoderException extends RuntimeException {
    public UnrecoverableVideoTranscoderException(final Exception exception) {
        super(exception);
    }

    public UnrecoverableVideoTranscoderException(final String message) {
        super(message);
    }

    public UnrecoverableVideoTranscoderException(final String message, final Exception exception) {
        super(message, exception);
    }
}
