/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;

import org.slf4j.Logger;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.WebRTCLogger;

/**
 * This util handles WebRTC initialization.
 */
public class WebRTCUtil {
    public enum Scope {
        /**
         * 'Diagnostic' scope is exclusive.
         */
        DIAGNOSTIC,
        /**
         * 'Call', 'Group Call' and 'Web Client' scope are shared with each
         * other but may not be shared with any other scope.
         */
        CALL_OR_GROUP_CALL_OR_WEB_CLIENT,
    }

    private static final Logger logger = LoggingUtil.getThreemaLogger("WebRTCUtil");

    private static @Nullable Scope initialized = null;

    private static Logging.Severity scopeToSeverity(final WebRTCUtil.Scope scope) {
        switch (scope) {
            case DIAGNOSTIC:
                return Logging.Severity.LS_VERBOSE;
            case CALL_OR_GROUP_CALL_OR_WEB_CLIENT:
                return Logging.Severity.LS_INFO;
            default:
                throw new IllegalStateException("Unknown WebRTC scope");
        }
    }

    /**
     * Initialise WebRTC peer connection factory globals for a specific scope.
     *
     * @param appContext The Android context to use. Make sure to use the application context!
     * @param scope      The scope (i.e. the use case for WebRTC).
     */
    @AnyThread
    public static synchronized void initializePeerConnectionFactory(final Context appContext, final Scope scope) {
        if (initialized == null) {
            logger.debug("Initializing peer connection factory globals");

            // Enable this to allow WebRTC trace logging. Note: Since this logs a lot of data,
            // it should only be enabled temporarily.
            final boolean enableVerboseInternalTracing = false;

            // Create WebRTC logger
            final WebRTCLogger webRtcLogger = new WebRTCLogger(scopeToSeverity(scope));

            // Initialize peer connection factory
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(enableVerboseInternalTracing)
                    .setInjectableLogger(webRtcLogger, webRtcLogger.severity)
                    .createInitializationOptions()
            );
            initialized = scope;
            logger.debug("Initialized peer connection factory globals");
        }

        // TODO(Post multi device): Change this, so the scopes are exclusive. Rationale is that the diagnostics
        //  scope may need to do more verbose logging and the logging should not be influenced
        //  by another scope running at the same time. This requires us to signal
        //  closure of a peer connection and its factory from the outside here in case
        //  the scope changes.
        //  Do not approach this before removing the web client code (because the web client
        //  will attempt to reinitialise itself in the background)!
        if (initialized != scope) {
            logger.warn("Changing scope from '{}' to '{}' without reinitialising libwebrtc", initialized, scope);
        }
    }

    /**
     * Convert an ICE candidate to a nice string representation.
     *
     * @param candidate The ICE candidate
     */
    @NonNull
    public static String iceCandidateToString(@NonNull IceCandidate candidate) {
        final IceCandidateParser.CandidateData parsed = IceCandidateParser.parse(candidate.sdp);
        if (parsed != null) {
            final StringBuilder builder = new StringBuilder();
            builder
                .append("[")
                .append(parsed.candType)
                .append("] ")
                .append(parsed.transport);
            if (parsed.tcptype != null) {
                builder.append("/").append(parsed.tcptype);
            }
            builder
                .append(" ")
                .append(parsed.connectionAddress)
                .append(":")
                .append(parsed.port);
            if (parsed.relAddr != null && parsed.relPort != null) {
                builder.append(" via ").append(parsed.relAddr).append(":").append(parsed.relPort);
            }
            return builder.toString();
        } else {
            return candidate.sdp;
        }
    }
}
