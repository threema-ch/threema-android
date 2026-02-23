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
 * Webclient sending connection info.
 */
@WorkerThread
public class ConnectionInfoUpdateHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ConnectionInfoUpdateHandler");

    @AnyThread
    public ConnectionInfoUpdateHandler() {
        super(Protocol.SUB_TYPE_CONNECTION_INFO);
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received update/connectionInfo, ignoring");
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
