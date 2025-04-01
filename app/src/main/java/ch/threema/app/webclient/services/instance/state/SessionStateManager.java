/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.webclient.services.instance.state;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.SaltyRTCBuilder;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Objects;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * This class manages and holds the state for a session.
 */
@WorkerThread
public class SessionStateManager {
    /**
     * Called in-sync when the session state is moving into the stopped state
     * and there are no pending wakeups for this session.
     */
    public interface StopHandler {
        void onStopped(@NonNull DisconnectContext reason);
    }

    private final Logger logger = LoggingUtil.getThreemaLogger("SessionStateManager");

    // Stop event handler
    @NonNull
    private final StopHandler stopHandler;

    // Session context that is passed from one state to another
    @NonNull
    private final SessionContext ctx;

    // Current state
    @NonNull
    private SessionState state;

    @AnyThread
    public SessionStateManager(
        final int sessionId,
        @NonNull final WebClientSessionModel model,
        @NonNull final HandlerExecutor handler,
        @NonNull final ServicesContainer services,
        @NonNull final StopHandler stopHandler
    ) {
        // Set logger prefix
        // Note: <session-id>.<affiliation-id>, where the affiliation id is initially not provided
        if (logger instanceof ThreemaLogger) {
            ((ThreemaLogger) logger).setPrefix(sessionId + ".null");
        }

        // Store stop event handler
        this.stopHandler = stopHandler;

        // Create initial session context
        this.ctx = new SessionContext(this, sessionId, model, handler, services);

        // Create initial state
        this.state = new SessionStateDisconnected(this.ctx);
    }

    /**
     * Get the current session state instance.
     * <p>
     * Note: Don't expose the SessionState instance to the outside!
     */
    @NonNull
    SessionState getInternalState() {
        return this.state;
    }

    /**
     * Get the current session state as an enum (not the instance).
     */
    @NonNull
    public WebClientSessionState getState() {
        return this.state.state;
    }

    // State transitions
    public void setConnecting(@NonNull final SaltyRTCBuilder builder, @Nullable final String affiliationId) {
        this.updateState(WebClientSessionState.CONNECTING, builder, affiliationId, null, null);
    }

    public void setConnected() {
        this.updateState(WebClientSessionState.CONNECTED, null, null, null, null);
    }

    public void setDisconnected(@NonNull final DisconnectContext reason) {
        this.updateState(WebClientSessionState.DISCONNECTED, null, null, reason, null);
    }

    public void setError(@NonNull final String reason) {
        this.updateState(WebClientSessionState.ERROR, null, null, null, reason);
    }

    /**
     * Update the current session state and fire state events.
     * <p>
     * Warning: This is a critical code section! Be careful when you touch this!
     */
    private void updateState(
        @NonNull final WebClientSessionState desiredState,
        @Nullable final SaltyRTCBuilder connectionBuilder,
        @Nullable final String connectionAffiliationId,
        @Nullable DisconnectContext disconnectReason,
        @Nullable final String errorReason
    ) {
        // Attempt to replace the state
        final SessionState currentState = this.state;
        SessionState newState;
        logger.info("Changing state from {} to {}", currentState.state, desiredState);
        try {
            switch (desiredState) {
                case CONNECTING:
                    newState = currentState.setConnecting(Objects.requireNonNull(connectionBuilder), connectionAffiliationId);
                    break;
                case CONNECTED:
                    newState = currentState.setConnected();
                    break;
                case DISCONNECTED:
                    newState = currentState.setDisconnected(Objects.requireNonNull(disconnectReason));
                    break;
                case ERROR:
                    newState = currentState.setError(Objects.requireNonNull(errorReason));
                    break;
                default:
                    logger.error("Unknown state: {}", desiredState);
                    return;
            }
        } catch (SessionState.IgnoredStateTransition error) {
            // Transition has been ignored - just log it
            logger.info(error.getMessage());
            return;
        } catch (SessionState.InvalidStateTransition error) {
            // Transition was not possible, move into error state
            if (logger.isErrorEnabled()) {
                logger.error("Could not perform state transition from {} to {}: {}", currentState.state, desiredState, error.getMessage());
            }
            newState = currentState.setError("Could not perform state transition from " + currentState.state + " to " + desiredState);
        }
        this.state = newState;

        // Notify listeners about the state change
        //
        // WARNING: If you start a state transition in a state event handler, it will break your neck!
        SessionState finalNewState = newState;
        WebClientListenerManager.serviceListener.handle(new ListenerManager.HandleListener<WebClientServiceListener>() {
            @Override
            @WorkerThread
            public void handle(WebClientServiceListener listener) {
                listener.onStateChanged(SessionStateManager.this.ctx.model, currentState.state, finalNewState.state);
            }
        });

        // Process pending wakeups when in the disconnected/error state
        //
        // Note: This MUST be done before notifying the listeners since the SessionAndroidService
        //       would otherwise stop the foreground service!
        if (newState.state == WebClientSessionState.DISCONNECTED || newState.state == WebClientSessionState.ERROR) {
            logger.info("Processing pending wakeups");
            this.ctx.services.sessionWakeUp.processPendingWakeups();

            // Notify listeners, if stopped
            // Note: The state may have changed now, so we only dispatch the stop event if the state
            //       instance (!) remained the same.
            if (this.ctx.manager.state == newState) {
                logger.info("No pending wakeups, stopping");
                final DisconnectContext reason = disconnectReason != null ? disconnectReason : DisconnectContext.unknown();

                // Raise to session instance first, so it can unregister events
                this.stopHandler.onStopped(reason);

                // Raise to all listeners
                WebClientListenerManager.serviceListener.handle(new ListenerManager.HandleListener<WebClientServiceListener>() {
                    @Override
                    @WorkerThread
                    public void handle(WebClientServiceListener listener) {
                        listener.onStopped(SessionStateManager.this.ctx.model, reason);
                    }
                });
            } else {
                logger.debug("Pending wakeups processed, continuing");
            }
        }
    }

    /**
     * Send a msgpack encoded message to the peer through the secure data channel.
     */
    public void send(@NonNull final ByteBuffer message, @NonNull final SendMode mode) {
        this.state.send(message, mode);
    }
}
