package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.app.webclient.Protocol;

@AnyThread
public class ContactTyping {
    /**
     * Return a {@link MsgpackObjectBuilder} to be used in the update/typing message data.
     */
    public static MsgpackObjectBuilder convert(boolean isTyping) {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(Protocol.ARGUMENT_IS_TYPING, isTyping);
        return builder;
    }
}
