package ch.threema.app.webclient.listeners;

import org.msgpack.value.MapValue;

import androidx.annotation.WorkerThread;

import ch.threema.storage.models.WebClientSessionModel;

/**
 * Listener that receives incoming Webclient DataChannel messages.
 */
@WorkerThread
public interface WebClientMessageListener {
    void onMessage(MapValue message);

    boolean handle(WebClientSessionModel sessionModel);
}
