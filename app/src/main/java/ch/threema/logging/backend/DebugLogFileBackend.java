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

package ch.threema.logging.backend;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.FileHandlingZipOutputStream;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.LogLevel;
import java8.util.concurrent.CompletableFuture;

/**
 * A logging backend that logs to the debug log file.
 * This backend is enabled initially to ensure that any crashes during app
 * startup are logged. Afterwards, it is disabled unless the the user enabled the debug log
 * explicitly in the settings.
 * The log file is deleted when calling `setEnabled(false)`.
 * A zipped log file can be requested with `getZipFile()`.
 */
public class DebugLogFileBackend implements LogBackend {
    // Constants
    private static final String TAG = BuildConfig.LOG_TAG;
    private static final String LOGFILE_NAME = "debug_log.txt";
    private static final String FALLBACK_LOGFILE_NAME = "fallback_debug_log.txt";

    // Static variables
    private static boolean enabled = true;
    private static File logFile = null;
    private static File fallbackLogFile = null;
    private static boolean hasSuccessFullyWrittenLogLine = false;
    private final @LogLevel int minLogLevel;

    // For tags starting with these prefixes, the package path is stripped
    private final static String[] STRIP_PREFIXES = {
        "ch.threema.app.",
        "ch.threema.domain.",
        "ch.threema.storage.",
        "ch.threema.",
    };

    // Worker thread
    private static @Nullable HandlerExecutor handler;

    /**
     * Create and start worker thread.
     */
    private static synchronized HandlerExecutor createHandler() {
        final HandlerThread handlerThread = new HandlerThread("DebugLogWorker");
        handlerThread.start();
        final Looper looper = handlerThread.getLooper();
        final Handler parent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            parent = Handler.createAsync(looper);
        } else {
            parent = new Handler(looper);
        }
        return new HandlerExecutor(parent);
    }

    /**
     * Return the handler for the worker thread. Start it first if necessary.
     */
    private static synchronized @NonNull HandlerExecutor getHandler() {
        if (handler == null) {
            handler = createHandler();
        }
        return handler;
    }

    public DebugLogFileBackend(@LogLevel int minLogLevel) {
        this.minLogLevel = minLogLevel;
    }

    @Override
    public boolean isEnabled(int level) {
        return enabled && level >= this.minLogLevel;
    }

    /**
     * Enable or disable logging to the debug log file.
     * By default, it is disabled.
     * When disabling the logging, then the file is deleted if it already exists.
     */
    public synchronized static void setEnabled(boolean enabled) {
        if (!DebugLogFileBackend.enabled && enabled) {
            hasSuccessFullyWrittenLogLine = false;
        }
        DebugLogFileBackend.enabled = enabled;
        if (!enabled) {
            final File file = getLogFile();
            if (file == null) {
                Log.e(TAG, "DebugLogFileBackend: Could not get debug log file path");
            }
            if (file != null && file.exists() && !file.delete()) {
                Log.e(TAG, "DebugLogFileBackend: Could not delete debug log file");
            }
            deleteFallbackLogFileIfExists();
        }
    }

    /**
     * Return whether debug log file logging is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * @hidden Used only for tests.
     */
    @TestOnly
    @NonNull
    static File getLogFilePath() {
        final File threemaDir = new File(ThreemaApplication.getAppContext().getExternalFilesDir(null), "log");
        return new File(threemaDir, LOGFILE_NAME);
    }

    @TestOnly
    @NonNull
    static File getFallbackLogFilePath() {
        return new File(ThreemaApplication.getAppContext().getFilesDir(), FALLBACK_LOGFILE_NAME);
    }

    /**
     * @return a {@link File} instance pointing to the debug log file or {@code null} if the log file directory could not be created.
     */
    @Nullable
    private static File getLogFile() {
        try {
            if (logFile == null || !logFile.exists()) {
                final File threemaDir = new File(ThreemaApplication.getAppContext().getExternalFilesDir(null), "log");
                if (!threemaDir.exists()) {
                    if (!threemaDir.mkdirs()) {
                        Log.e(TAG, "DebugLogFileBackend: Could not create threema directory");
                        return null;
                    }
                }
                logFile = new File(threemaDir, LOGFILE_NAME);
            }
            return logFile;
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * @return a {@link File} instance pointing to the fallback debug log file.
     * This fallback should only be used if the default log file cannot be used.
     */
    @NonNull
    private static File getFallbackLogFile() {
        if (fallbackLogFile == null) {
            final File filesDir = ThreemaApplication.getAppContext().getFilesDir();
            fallbackLogFile = new File(filesDir, FALLBACK_LOGFILE_NAME);
        }
        return fallbackLogFile;
    }

    private static void deleteFallbackLogFileIfExists() {
        final File filesDir = ThreemaApplication.getAppContext().getFilesDir();
        final File fallbackLogFile = new File(filesDir, FALLBACK_LOGFILE_NAME);
        if (fallbackLogFile.exists() && !fallbackLogFile.delete()) {
            Log.e(TAG, "DebugLogFileBackend: Could not delete fallback debug log file");
        }
    }

    /**
     * If the logger is enabled, write the log asynchronously to the log file.
     * I/O is dispatched to the handler thread.
     * A CompletableFuture is returned, which resolves once processing is finished.
     * The returned value is TRUE if the log was written successfully,
     * FALSE if writing the log failed, and null if the logger was not enabled.
     *
     * @see #print(int, String, Throwable, String)
     */
    synchronized CompletableFuture<Boolean> printAsync(
        @LogLevel int level,
        @NonNull String tag,
        @Nullable Throwable throwable,
        @Nullable String message
    ) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!this.isEnabled(level)) {
            future.complete(null);
            return future;
        }

        // Dispatch I/O to worker thread.
        getHandler().post(() -> {
            // Get log level string
            String levelString;
            switch (level) {
                case Log.VERBOSE:
                    levelString = "TRACE";
                    break;
                case Log.DEBUG:
                    levelString = "DEBUG";
                    break;
                case Log.INFO:
                    levelString = "INFO ";
                    break;
                case Log.WARN:
                    levelString = "WARN ";
                    break;
                case Log.ERROR:
                    levelString = "ERROR";
                    break;
                default:
                    levelString = "?    ";
            }

            // Prepare log text
            final Date now = new Date();
            String logLine = now.toString()
                + '\t' + levelString
                + " " + LoggingUtil.cleanTag(tag, STRIP_PREFIXES) + ": ";
            if (message == null) {
                if (throwable != null) {
                    logLine += Log.getStackTraceString(throwable);
                }
            } else {
                if (throwable == null) {
                    logLine += message;
                } else {
                    logLine += message + '\n' + Log.getStackTraceString(throwable);
                }
            }

            // Write to logfile
            boolean success = false;
            @Nullable final File defaultLogFile = getLogFile();
            if (defaultLogFile != null) {
                success = writeToFile(defaultLogFile, logLine);
                if (success && !hasSuccessFullyWrittenLogLine) {
                    deleteFallbackLogFileIfExists();
                    hasSuccessFullyWrittenLogLine = true;
                }
            }
            if (!success && !hasSuccessFullyWrittenLogLine) {
                success = writeToFile(getFallbackLogFile(), logLine);
            }
            future.complete(success);
        });

        return future;
    }

    private static boolean writeToFile(@NonNull File file, @NonNull String logLine) {
        try (
            final FileWriter fw = new FileWriter(file, true);
            final PrintWriter pw = new PrintWriter(fw)
        ) {
            pw.println(logLine);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * If the logger is enabled, write the log to the log file.
     * Note: I/O is done asynchronously, so the log may not yet be fully written
     * to storage when this method returns!
     *
     * @param level     The log level
     * @param tag       The log tag
     * @param throwable A throwable (may be null)
     * @param message   A message (may be null)
     */
    @Override
    public synchronized void print(
        @LogLevel int level,
        @NonNull String tag,
        @Nullable Throwable throwable,
        @Nullable String message
    ) {
        this.printAsync(level, tag, throwable, message);
    }

    @Override
    public synchronized void print(@LogLevel int level, @NonNull String tag, @Nullable Throwable throwable, @NonNull String messageFormat, Object... args) {
        if (!this.isEnabled(level)) {
            return;
        }
        try {
            this.print(level, tag, throwable, MessageFormatter.arrayFormat(messageFormat, args).getMessage());
        } catch (Exception e) { // Never crash
            this.print(level, tag, throwable, messageFormat);
        }
    }

    @Nullable
    public static File createZipFile(@NonNull Context context) {
        final File tempDebugLogArchive = new File(context.getCacheDir(), "debug_log.zip");
        deleteIfExists(tempDebugLogArchive);
        if (createZipFile(tempDebugLogArchive)) {
            return tempDebugLogArchive;
        }
        return null;
    }

    private static void deleteIfExists(@NonNull File file) {
        try {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "DebugLogFileBackend: Could not delete " + file.getPath());
            }
        } catch (SecurityException e) {
            Log.w(TAG, "DebugLogFileBackend: Failed to access " + file.getPath(), e);
        }
    }

    private static boolean createZipFile(@NonNull File zipFile) {
        try (
            final FileHandlingZipOutputStream zipOutputStream = FileHandlingZipOutputStream.initializeZipOutputStream(zipFile, null)
        ) {
            writeToZipFileIfPossible(getLogFile(), zipOutputStream);
            writeToZipFileIfPossible(getFallbackLogFile(), zipOutputStream);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "DebugLogFileBackend: Failed to create zip file " + zipFile.getPath(), e);
            return false;
        }
    }

    private static void writeToZipFileIfPossible(
        @Nullable File file,
        @NonNull FileHandlingZipOutputStream zipOutputStream
    ) throws IOException, SecurityException {
        if (file == null || !file.exists()) {
            return;
        }
        try(final InputStream inputStream = new FileInputStream(file)) {
            final ZipParameters parameters = createZipParameters(file.getName());
            zipOutputStream.putNextEntry(parameters);
            IOUtils.copy(inputStream, zipOutputStream, 16384);
            zipOutputStream.closeEntry();
        }
    }

    private static ZipParameters createZipParameters(String filenameInZip) {
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(CompressionLevel.NORMAL);
        parameters.setFileNameInZip(filenameInZip);
        return parameters;
    }

}
