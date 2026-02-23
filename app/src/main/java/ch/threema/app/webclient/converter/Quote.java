package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.utils.QuoteUtil.QuoteContent;
import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class Quote extends Converter {
    private static final String FIELD_IDENTITY = "identity";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_MESSAGE_ID = "messageId";
    private static final String FIELD_MESSAGE = "message";

    /**
     * Create a Quote object.
     */
    public static MsgpackObjectBuilder convert(@NonNull QuoteContent quoteContent) throws ConversionException {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(FIELD_IDENTITY, quoteContent.identity);
        builder.put(FIELD_TEXT, quoteContent.quotedText);
        if (quoteContent.isQuoteV2()) {
            builder.put(FIELD_MESSAGE_ID, quoteContent.quotedMessageId);
            if (quoteContent.quotedMessageModel != null && quoteContent.messageReceiver != null) {
                builder.put(FIELD_MESSAGE, Message.convert(
                    quoteContent.quotedMessageModel,
                    quoteContent.messageReceiver,
                    false,
                    Message.DETAILS_NO_QUOTE
                ));
            }
        }
        return builder;
    }
}
