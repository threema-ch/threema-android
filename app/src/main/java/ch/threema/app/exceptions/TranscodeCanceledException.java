package ch.threema.app.exceptions;

import ch.threema.base.ThreemaException;

public class TranscodeCanceledException extends ThreemaException {
    public TranscodeCanceledException() {
        super("Transcode canceled");
    }
}
