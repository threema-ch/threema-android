package ch.threema.app.webclient.services.instance;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.saltyrtc.client.crypto.CryptoException;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.nio.ByteBuffer;

import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Interface of the Webclient service.
 */
@WorkerThread
public interface SessionInstanceService {
    /**
     * Return whether this session is in a non-terminal state.
     */
    boolean isRunning();

    /**
     * Return the current state of the session.
     */
    @NonNull
    WebClientSessionState getState();

    /**
     * Return whether the session needs to be restarted (due to a different affiliation id).
     */
    boolean needsRestart(@Nullable String affiliationId);

    /**
     * Return the session model.
     */
    @AnyThread
    @NonNull
    WebClientSessionModel getModel();

    /**
     * Start a session.
     * <p>
     * Note: Will be ignored when the session is running!
     */
    void start(@NonNull byte[] permanentKey, @NonNull byte[] authToken, @Nullable String affiliationId);

    /**
     * Resume a session by the saved permanent key.
     * <p>
     * Note: Will be ignored when the session is running!
     */
    void resume(@Nullable String affiliationId) throws CryptoException;

    /**
     * Stop a session.
     * <p>
     * Note: Will be ignored when the session is not running!
     */
    void stop(@NonNull DisconnectContext reason);

    /**
     * Send data to the peer.
     *
     * @param message Msgpack encoded bytes
     */
    void send(@NonNull ByteBuffer message, @NonNull SendMode mode);
}
