package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Webclient sending key persisted information
 */
@WorkerThread
public class KeyPersistedRequestHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("KeyPersistedRequestHandler");

    private Listener listener;

    @WorkerThread
    public interface Listener {
        void onReceived();
    }

    @AnyThread
    public KeyPersistedRequestHandler(Listener listener) {
        super(Protocol.SUB_TYPE_KEY_PERSISTED);
        this.listener = listener;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received key persisted request");
        if (this.listener != null) {
            this.listener.onReceived();
        }
        //do not respond
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
