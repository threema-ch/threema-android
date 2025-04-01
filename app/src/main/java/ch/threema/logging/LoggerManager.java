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

package ch.threema.logging;

import android.util.Log;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.logging.backend.LogBackend;
import ch.threema.logging.backend.LogcatBackend;

/**
 * This is where loggers are created and where backends are configured.
 * <p>
 * Do not use this manager directly, instead log through SLF4J! For example:
 * <p>
 * private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaApplication");
 * ...
 * logger.debug("This is a debug log");
 */
public class LoggerManager {
    private static final Map<String, Logger> LOGGER_CACHE = new WeakHashMap<>();

    // Don't allow instantiation
    public LoggerManager() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return the minimal log level for the logger with the specified name.
     */
    private static @LogLevel int getMinLogLevel(String name) {
        if (name.startsWith("ch.threema")) {
            return Log.INFO;
        }
        if (name.equals("Validation")) {
            return Log.INFO;
        }
        if (name.startsWith("SaltyRTC.") || name.startsWith("org.saltyrtc")) {
            return Log.INFO;
        }
        if (name.startsWith("libwebrtc") || name.startsWith("org.webrtc")) {
            return Log.INFO;
        }
        return Log.WARN;
    }

    /**
     * Return logger with the specified name.
     */
    public static Logger getLogger(String name) {
        Logger logger;

        // Cache lookup
        synchronized (LOGGER_CACHE) {
            logger = LOGGER_CACHE.get(name);
        }
        if (logger != null) {
            return logger;
        }

        // Get minimal log level
        int minLogLevel = LoggerManager.getMinLogLevel(name);

        // Initialize backends
        final List<LogBackend> backends = new ArrayList<>();
        if (BuildConfig.DEBUG || BuildFlavor.getCurrent().isSandbox()) {
            // Enable logging to logcat only for debug and sandbox builds
            backends.add(new LogcatBackend(Log.VERBOSE));
        }
        backends.add(new DebugLogFileBackend(minLogLevel));

        // Initialize and cache logger
        logger = new ThreemaLogger(name, backends);
        synchronized (LOGGER_CACHE) {
            LOGGER_CACHE.put(logger.getName(), logger);
        }

        return logger;
    }
}
