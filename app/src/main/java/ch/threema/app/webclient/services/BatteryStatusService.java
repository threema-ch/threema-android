package ch.threema.app.webclient.services;

import androidx.annotation.WorkerThread;

import ch.threema.storage.models.WebClientSessionModel;

/**
 * Handling the WebClient battery status subscription.
 */
@WorkerThread
public interface BatteryStatusService {
    /**
     * Register a new session that wants battery status updates.
     */
    void acquire(WebClientSessionModel session);

    /**
     * Deregister a session that doesn't need battery status updates anymore.
     * The implementation must not crash if the resources have already been released!
     */
    void release(WebClientSessionModel session);
}
