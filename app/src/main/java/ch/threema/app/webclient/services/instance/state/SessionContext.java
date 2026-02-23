package ch.threema.app.webclient.services.instance.state;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Context passed around by the session state classes.
 */
@WorkerThread
public class SessionContext {
    // State manager
    @NonNull
    final SessionStateManager manager;

    // Session
    final int sessionId;
    @NonNull
    final WebClientSessionModel model;

    // Connection ID. Will be incremented every time the state changes to CONNECTING.
    int connectionId = -1;

    // Affiliation ID. Will be changed every time the client wakes up the device.
    @Nullable
    String affiliationId;

    // Worker thread
    @NonNull
    final HandlerExecutor handler;

    // Services
    @NonNull
    final ServicesContainer services;

    @AnyThread
    public SessionContext(
        @NonNull final SessionStateManager manager,
        final int sessionId,
        @NonNull final WebClientSessionModel model,
        @NonNull final HandlerExecutor handler,
        @NonNull final ServicesContainer services) {
        this.manager = manager;
        this.sessionId = sessionId;
        this.model = model;
        this.handler = handler;
        this.services = services;
    }

    /**
     * Acquire wakelocks and other resources that need to be active during a session.
     */
    void acquireResources() {
        this.services.wakeLock.acquire(this.model);
        this.services.batteryStatus.acquire(this.model);
    }

    /**
     * Release all held wakelocks and other resources.
     */
    void releaseResources() {
        this.services.wakeLock.release(this.model);
        this.services.batteryStatus.release(this.model);
    }

    /**
     * Return whether IPv6 is allowed.
     */
    boolean allowIpv6() {
        return this.services.preference.allowWebrtcIpv6();
    }
}
