package ch.threema.app.webclient.services;

import androidx.annotation.WorkerThread;

import ch.threema.storage.models.WebClientSessionModel;

/**
 * Handling the WebClient Wakelock
 */
@WorkerThread
public interface WakeLockService {
    /**
     * acquire a new (or existing) wakelock for given session
     *
     * @param session session who acquire the wakelock
     * @return success
     */
    boolean acquire(WebClientSessionModel session);

    /**
     * return true if one ore more webclient session
     * acquired the wakelock
     *
     * @return if the wakelock is held
     */
    boolean isHeld();

    /**
     * Release the wakelock for given session.
     * The implementation must not crash if the resources have already been released!
     *
     * @return success
     */
    boolean release(WebClientSessionModel session);

    /**
     * release all running webclient wakelocks
     *
     * @return success
     */
    boolean releaseAll();
}
