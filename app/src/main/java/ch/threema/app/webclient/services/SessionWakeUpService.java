package ch.threema.app.webclient.services;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Handle Web wakeups (via push notifications) that should be processed later.
 */
@AnyThread
public interface SessionWakeUpService {
    /**
     * Resume a web client session. If the session cannot be resumed immediately, a pending wakeup
     * will be stored.
     * <p>
     * Note: The starting operation will be performed asynchronously from the worker thread.
     *
     * @param publicKeySha256String The SHA256 hash of the public session key.
     * @param version               The protocol version
     * @param affiliationId         An optional affiliation id assigned to a group of connection attempts.
     */
    void resume(@NonNull String publicKeySha256String, int version, @Nullable String affiliationId);

    /**
     * Go through all pending wakeups and start the corresponding sessions, if possible.
     * <p>
     * Note: This can only be run from the worker thread.
     */
    @WorkerThread
    void processPendingWakeups();

    /**
     * Go through all pending wakeups and start the corresponding sessions, if possible.
     * <p>
     * Note: The wakeup will be dispatched asynchronously to the worker thread.
     */
    void processPendingWakeupsAsync();

    /**
     * Discard all pending wakeups.
     */
    @WorkerThread
    void discardPendingWakeups();
}
