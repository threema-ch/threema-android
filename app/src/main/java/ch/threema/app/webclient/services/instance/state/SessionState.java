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

import org.saltyrtc.client.SaltyRTCBuilder;
import org.slf4j.Logger;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.nio.ByteBuffer;

import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.ThreemaLogger;

/**
 * A session state.
 */
@WorkerThread
public abstract class SessionState {
    /**
     * Fired when an invalid state transition has been attempted.
     */
    static class InvalidStateTransition extends Exception {
        public InvalidStateTransition(@NonNull final String message) {
            super(message);
        }
    }

    /**
     * Fired when a state transition request has been ignored.
     */
    static class IgnoredStateTransition extends Exception {
        public IgnoredStateTransition(@NonNull final String message) {
            super(message);
        }
    }

    // Logging
    @NonNull
    protected final Logger logger = LoggingUtil.getThreemaLogger("SessionState");

    // Session context
    @NonNull
    protected final SessionContext ctx;

    // State name
    @NonNull
    public final WebClientSessionState state;

    @AnyThread
    protected SessionState(@NonNull final WebClientSessionState state, @NonNull final SessionContext ctx) {
        this.state = state;
        this.ctx = ctx;

        // Set logger prefix
        if (logger instanceof ThreemaLogger) {
            ((ThreemaLogger) logger).setPrefix(ctx.sessionId + "." + ctx.affiliationId + "/" + this.state.name());
        }
    }

    /**
     * Send a msgpack encoded message to the peer through the secure data channel.
     */
    void send(@NonNull final ByteBuffer message, @NonNull final SendMode mode) {
        // Default implementation. Override if sending is possible.
        logger.error("Cannot send a message in this state");
    }

    // State change methods
    // Note: All transitions but 'error' are invalid by default and must be explicitly enabled.
    @NonNull
    SessionStateConnecting setConnecting(@NonNull final SaltyRTCBuilder builder, @Nullable final String affiliationId)
        throws InvalidStateTransition, IgnoredStateTransition {
        throw new InvalidStateTransition("Transition to 'connecting' state not allowed");
    }

    @NonNull
    SessionStateConnected setConnected()
        throws InvalidStateTransition, IgnoredStateTransition {
        throw new InvalidStateTransition("Transition to 'connected' state not allowed");
    }

    @NonNull
    SessionStateDisconnected setDisconnected(@NonNull final DisconnectContext reason)
        throws InvalidStateTransition, IgnoredStateTransition {
        throw new InvalidStateTransition("Transition to 'disconnected' state not allowed");
    }

    @NonNull
    abstract SessionStateError setError(@NonNull final String reason);
}
