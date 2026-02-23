package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import ch.threema.app.services.ContactService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

@WorkerThread
public class IsTypingHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("IsTypingHandler");

    @NonNull
    private final ContactService contactService;

    @AnyThread
    public IsTypingHandler(@NonNull ContactService userService) {
        super(Protocol.SUB_TYPE_TYPING);
        this.contactService = userService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received typing update");

        // Get args
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_ID,
        });
        final String identity = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

        // Get data
        final Map<String, Value> data = this.getData(message, false, new String[]{
            Protocol.ARGUMENT_IS_TYPING,
        });
        boolean isTyping = data.get(Protocol.ARGUMENT_IS_TYPING).asBooleanValue().getBoolean();

        this.contactService.sendTypingIndicator(identity, isTyping);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}
