/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.logging;

import android.annotation.SuppressLint;
import android.util.Log;

import org.slf4j.Logger;
import org.webrtc.Loggable;
import org.webrtc.Logging;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * An adapter that sends WebRTC native logs to the SLFJ logger.
 */
public class WebRTCLogger implements Loggable {
    @SuppressLint("LoggerName")
    private static final Logger logger = getThreemaLogger("libwebrtc");

    public final Logging.Severity severity;
    private final @LogLevel int minLevel;

    private enum ThrottledMessageKind {
        // TODO(ANDR-2011): Remove once resolved
        ANOTHER_UNSIGNALLED_SSRC_PACKET_ARRIVED,
        // TODO(ANDR-2011): Remove once resolved
        UNEXPECTED_END_OF_PACKET,
        // TODO(ANDR-2011): Remove once resolved
        RTCP_BLOCKS_WERE_SKIPPED,
        MESSAGE_REVALIDATION
    }

    private final Map<ThrottledMessageKind, Long> encounteredMessages = new HashMap<>();

    private static @LogLevel int severityToLogLevel(final Logging.Severity severity) {
        switch (severity) {
            case LS_NONE:
                throw new IllegalStateException("May not use severity 'NONE'");
            case LS_VERBOSE:
                return Log.DEBUG;
            case LS_INFO:
                return Log.INFO;
            case LS_WARNING:
                return Log.WARN;
            case LS_ERROR:
                return Log.ERROR;
            default:
                throw new IllegalStateException("Unknown severity");
        }
    }

    public WebRTCLogger(final Logging.Severity severity) {
        this.severity = severity;
        this.minLevel = severityToLogLevel(severity);
    }

    @Override
    public void onLogMessage(@NonNull String msg, @NonNull Logging.Severity severity, @NonNull String file) {
        final String fullMsg = file + msg.trim();
        switch (severity) {
            case LS_VERBOSE:
                if (minLevel <= Log.DEBUG) {
                    // A PCAP of all data channel messages is a bit too much...
                    if (file.equals("text_pcap_packet_observer.cc")) {
                        return;
                    }

                    logger.debug(fullMsg);
                }
                break;
            case LS_INFO:
                if (minLevel <= Log.INFO) {
                    if (file.equals("stun.cc")
                        && msg.contains("Message revalidation, old status was 2")
                        && shouldDiscard(ThrottledMessageKind.MESSAGE_REVALIDATION)) {
                        return;
                    }
                    logger.info(fullMsg);
                }
                break;
            case LS_WARNING:
                if (minLevel <= Log.WARN) {
                    // This is the SFU probing for available bandwidth, so it's fine and can be
                    // ignored.
                    if (file.equals("rtp_transport.cc") &&
                        msg.contains("Failed to demux RTP packet") &&
                        msg.contains("MID=probator")) {
                        return;
                    }

                    // TODO(ANDR-2011): Remove once resolved
                    if (file.equals("webrtc_video_engine.cc") &&
                        msg.contains("Another unsignalled ssrc packet arrived") &&
                        shouldDiscard(ThrottledMessageKind.ANOTHER_UNSIGNALLED_SSRC_PACKET_ARRIVED)) {
                        return;
                    }
                    // TODO(ANDR-2011): Remove once resolved
                    if (file.equals("sdes.cc") &&
                        msg.contains("Unexpected end of packet while reading chunk") &&
                        shouldDiscard(ThrottledMessageKind.UNEXPECTED_END_OF_PACKET)) {
                        return;
                    }
                    // TODO(ANDR-2011): Remove once resolved
                    if (file.equals("rtcp_receiver.cc") &&
                        msg.contains("RTCP blocks were skipped due to being malformed") &&
                        shouldDiscard(ThrottledMessageKind.RTCP_BLOCKS_WERE_SKIPPED)) {
                        return;
                    }

                    logger.warn(fullMsg);
                }
                break;
            case LS_ERROR:
                if (minLevel <= Log.ERROR) {
                    logger.error(fullMsg);
                }
                break;
        }
    }

    private boolean shouldDiscard(final ThrottledMessageKind kind) {
        final Long lastEncounter = encounteredMessages.get(kind);
        final long now = System.currentTimeMillis();

        // Only discard if last encounter was < 5s ago
        if (lastEncounter == null || now - lastEncounter >= 5000) {
            encounteredMessages.put(kind, now);
            return false;
        } else {
            return true;
        }
    }
}
