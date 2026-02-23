package ch.threema.app.webclient.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.storage.models.WebClientSessionModel;

@AnyThread
public interface WebClientServiceListener {
    /**
     * Called after the web client service has been enabled.
     */
    default void onEnabled() {
    }

    /**
     * Called after the web client service has been disabled.
     */
    default void onDisabled() {
    }

    /**
     * Called after the connection with the browser has been established
     * and the browser has sent the client info to the app.
     */
    default void onStarted(
        @NonNull WebClientSessionModel model,
        @NonNull byte[] permanentKey,
        @NonNull String browser
    ) {
    }

    /**
     * Called when the browser tells us that it has persisted the session to local storage.
     */
    default void onKeyPersisted(@NonNull WebClientSessionModel model, boolean persisted) {
    }

    /**
     * Called when the webclient session state for the specified session has changed.
     * <p>
     * WARNING: This MUST NOT be used to change the state of the web client session in any way.
     */
    default void onStateChanged(
        @NonNull WebClientSessionModel model,
        @NonNull WebClientSessionState oldState,
        @NonNull WebClientSessionState newState
    ) {
    }

    /**
     * Called when the webclient session state has changed to DISCONNECTED.
     */
    default void onStopped(
        @NonNull WebClientSessionModel model,
        @NonNull DisconnectContext reason
    ) {
    }

    /**
     * Called when the FCM push token has been changed and after that token
     * has been sent to the browser.
     */
    default void onPushTokenChanged(
        @NonNull WebClientSessionModel model,
        @Nullable String newPushToken
    ) {
    }
}
