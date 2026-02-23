package ch.threema.app.video.transcoder.audio;

import android.media.MediaFormat;

public class UnsupportedAudioFormatException extends Exception {

    public UnsupportedAudioFormatException(final MediaFormat inputFormat) {
        super(inputFormat.toString());
    }

    public UnsupportedAudioFormatException(final String message) {
        super(message);
    }

    public UnsupportedAudioFormatException(final String msg, final IllegalStateException cause) {
        super(msg, cause);
    }
}
