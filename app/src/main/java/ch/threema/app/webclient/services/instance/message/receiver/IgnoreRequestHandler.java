package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * A handler that logs and ignores incoming messages for the specified subtype.
 */
@WorkerThread
public class IgnoreRequestHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("IgnoreRequestHandler");

    private final @NonNull String type;

    @AnyThread
    public IgnoreRequestHandler(final @NonNull String type, final @NonNull String subType) {
        super(subType);
        this.type = type;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Ignoring incoming {}/{} message", this.type, this.subType);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
