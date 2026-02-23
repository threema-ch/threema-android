package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class MessageState extends Converter {
    public static final String DELIVERED = "delivered";
    public static final String READ = "read";
    public static final String SENDFAILED = "send-failed";
    public static final String SENT = "sent";
    public static final String PENDING = "pending";
    public static final String SENDING = "sending";

    public static String convert(ch.threema.storage.models.MessageState state) throws ConversionException {
        try {
            switch (state) {
                case DELIVERED:
                    return MessageState.DELIVERED;
                case READ:
                case USERACK:
                case USERDEC:
                case CONSUMED:
                    return MessageState.READ;
                case SENDFAILED:
                case FS_KEY_MISMATCH:
                    return MessageState.SENDFAILED;
                case SENT:
                    return MessageState.SENT;
                case PENDING:
                case TRANSCODING:
                case UPLOADING:
                    return MessageState.PENDING;
                case SENDING:
                    return MessageState.SENDING;
                default:
                    throw new ConversionException("Unknown message state: " + state);
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }
}
