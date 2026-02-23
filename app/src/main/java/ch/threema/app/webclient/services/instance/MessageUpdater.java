package ch.threema.app.webclient.services.instance;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

/**
 * A message updater is a handler that continually sends updates to the webclient.
 * It can be registered and unregistered.
 */
@WorkerThread
abstract public class MessageUpdater extends MessageHandler {
    @AnyThread
    public MessageUpdater(String subType) {
        super(subType);
    }

    @AnyThread
    public abstract void register();

    @AnyThread
    public abstract void unregister();
}
