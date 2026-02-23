package ch.threema.app.webclient.services.instance;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

/**
 * Receive messages from the webclient.
 */
@WorkerThread
abstract public class MessageReceiver extends MessageHandler {
    @AnyThread
    public MessageReceiver(String subType) {
        super(subType);
    }

    protected abstract void receive(Map<String, Value> message) throws MessagePackException;

    protected boolean receive(String subType, Map<String, Value> message) throws MessagePackException {
        // Are we receiving this sub type?
        if (subType.equals(this.subType)) {
            this.receive(message);
            return true;
        } else {
            return false;
        }
    }

    /**
     * If this method returns `true`, then after processing the data the dispatcher will check
     * whether any outgoing messages have been enqueued. If there are outgoing messages in the queue
     * and the app is disconnected, the connection will be opened to send those messages.
     * <p>
     * A handler like the `TextMessageCreateHandler` should return `true`, while a handler like
     * `BatteryStatusRequestHandler` should return `false`.
     */
    protected abstract boolean maybeNeedsConnection();
}
