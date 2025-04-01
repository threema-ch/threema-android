/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.webclient.services.instance;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;

/**
 * Capture the disconnect context:
 * E.g. whether the disconnect was requested by us, whether to forget the session, etc.
 * <p>
 * This class is immutable.
 */
@AnyThread
public abstract class DisconnectContext {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DisconnectContext");

    // The session SHALL be stopped by the receiving peer.
    public final static int REASON_SESSION_STOPPED = 1;
    // The session MUST be stopped and deleted by the receiving peer.
    public final static int REASON_SESSION_DELETED = 2;
    // The web client has been disabled in the app and the receiving peer SHALL stop the session.
    public final static int REASON_WEBCLIENT_DISABLED = 3;
    // The session has been replaced by another and the receiving peer MUST stop the session.
    public final static int REASON_SESSION_REPLACED = 4;
    // The session MUST be stopped immediately because the device ran out of memory
    public final static int REASON_OUT_OF_MEMORY = 5;
    // The session MUST be stopped immediately due to a fatal error
    public final static int REASON_ERROR = 6;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        REASON_SESSION_STOPPED,
        REASON_SESSION_DELETED,
        REASON_WEBCLIENT_DISABLED,
        REASON_SESSION_REPLACED,
        REASON_OUT_OF_MEMORY,
        REASON_ERROR,
    })
    public @interface DisconnectReason {
    }

    protected final boolean requestedByUs;
    @Nullable
    protected final @DisconnectReason Integer reason;

    /**
     * Whether the session should be removed.
     */
    public boolean shouldForget() {
        if (this.reason == null) {
            return false;
        }
        switch (this.reason) {
            case REASON_SESSION_DELETED:
                return true;
            case REASON_SESSION_STOPPED:
            case REASON_WEBCLIENT_DISABLED:
            case REASON_SESSION_REPLACED:
            case REASON_OUT_OF_MEMORY:
            case REASON_ERROR:
                return false;
            default:
                logger.error("Invalid disconnect reason: {}", reason);
                return false;
        }
    }

    @Override
    public String toString() {
        return "DisconnectContext{" +
            "requestedByUs=" + requestedByUs +
            ", reason=" + reason +
            '}';
    }

    public static class ByUs extends DisconnectContext {
        ByUs(@DisconnectReason int reason) {
            super(true, reason);
        }

        public @DisconnectReason int getReason() {
            return Objects.requireNonNull(this.reason);
        }
    }

    public static class ByPeer extends DisconnectContext {
        public ByPeer(@DisconnectReason int reason) {
            super(false, reason);
        }

        public @DisconnectReason int getReason() {
            return Objects.requireNonNull(this.reason);
        }
    }

    public static class Unknown extends DisconnectContext {
        public Unknown() {
            super(false, null);
        }
    }

    protected DisconnectContext(final boolean requestedByUs, @DisconnectReason @NonNull final Integer reason) {
        this.requestedByUs = requestedByUs;
        this.reason = reason;
    }

    /**
     * The disconnect was requested by us.
     *
     * @param reason The disconnect reason
     */
    public static DisconnectContext.ByUs byUs(@DisconnectReason final int reason) {
        return new ByUs(reason);
    }

    /**
     * The disconnect was requested by the peer.
     */
    public static DisconnectContext.ByPeer byPeer(@DisconnectReason final int reason) {
        return new ByPeer(reason);
    }

    /**
     * The disconnect reason is unknown or irrelevant.
     */
    public static DisconnectContext.Unknown unknown() {
        return new Unknown();
    }
}
