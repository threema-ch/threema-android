package ch.threema.app.webclient.services;

import org.saltyrtc.client.exceptions.InvalidKeyException;

import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.webclient.exceptions.HandshakeException;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.services.instance.SessionInstanceService;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.SessionScoped;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.WebClientSessionModel;

@SessionScoped
@AnyThread
public interface SessionService {
    /**
     * Enable or disable the web client.
     * <p>
     * This method is asynchronous. To get notified when the operation is done,
     * implement `WebClientServiceListener`.
     */
    void setEnabled(boolean enable);

    /**
     * Return whether or not the web client is enabled.
     * <p>
     * This considers the preferences as well as MDM parameters and licensing.
     */
    boolean isEnabled();

    /**
     * Return whether or not there are currently running sessions.
     */
    boolean hasRunningSessions();

    /**
     * Return a list of all sessions.
     */
    @NonNull
    List<WebClientSessionModel> getAllSessionModels();

    /**
     * Get or create a service by an existing session model.
     */
    @Nullable
    SessionInstanceService getInstanceService(
        @NonNull WebClientSessionModel model,
        boolean createIfNotExists
    );

    /**
     * Get or create a service by the public key of the initiator.
     */
    @Nullable
    SessionInstanceService getInstanceService(
        @NonNull String publicKeySha256String,
        boolean createIfNotExists
    );

    /**
     * Create a new session and start the connection asynchronously.
     */
    @NonNull
    WebClientSessionModel create(
        @NonNull byte[] permanentyKey,
        @NonNull byte[] authToken,
        @NonNull String saltyRtcHost,
        int saltyRtcPort,
        @Nullable byte[] serverKey,
        boolean isPermanent,
        boolean isSelfHosted,
        @Nullable String affiliationId
    ) throws ThreemaException, HandshakeException, InvalidKeyException;

    /**
     * Get the number of running sessions.
     */
    long getRunningSessionsCount();

    /**
     * Return the current state of a session.
     */
    @NonNull
    WebClientSessionState getState(@NonNull final WebClientSessionModel model);

    /**
     * Return whether a session is currently running.
     */
    boolean isRunning(@NonNull WebClientSessionModel model);

    /**
     * Stop session.
     */
    void stop(@NonNull WebClientSessionModel model, @NonNull DisconnectContext reason);

    /**
     * Stop all sessions.
     */
    void stopAll(@NonNull DisconnectContext reason);
}
