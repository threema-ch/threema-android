package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

/**
 * https://threema-ch.github.io/app-remote-protocol/message-update-connectionInfo-bidirectional.html
 * <p>
 * For now, we don't implement connection resuming, so we just return dummy data that will
 * result in a new session being established.
 */
@AnyThread
public class ConnectionInfo extends Converter {
    // Top level keys
    private final static String ID = "id";
    private final static String RESUME = "resume";

    public static MsgpackObjectBuilder convert() {
        final MsgpackObjectBuilder data = new MsgpackObjectBuilder();
        data.put(ID, new byte[]{
            // Fake it till you make it
            1, 2, 3, 4, 5, 6, 7, 8,
            1, 2, 3, 4, 5, 6, 7, 8,
            1, 2, 3, 4, 5, 6, 7, 8,
            1, 2, 3, 4, 5, 6, 7, 8,
        });
        data.maybePut(RESUME, (Integer) null);
        return data;
    }
}
