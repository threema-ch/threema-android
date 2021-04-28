/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.FileService;
import ch.threema.app.utils.ZipUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.logging.LogLevel;
import ch.threema.logging.LoggingUtil;
import java8.util.concurrent.CompletableFuture;

/**
 * A logging backend that logs to the debug log file.
 *
 * This backend is only enabled if the user enabled the debug log.
 *
 * The log file is deleted when calling `setEnabled(false)`.
 *
 * A zipped log file can be requested with `getZipFile()`.
 */
public class DebugLogFileBackend implements LogBackend {
	// Constants
	private static final String TAG = "3ma";
	private static final String LOGFILE_NAME = "debug_log.txt";

	// Static variables
	private static boolean enabled = false;
	private static File logFile = null;
	private final @LogLevel int minLogLevel;

	// For tags starting with these prefixes, the package path is stripped
	private final static String[] STRIP_PREFIXES = {
		"ch.threema.app.",
		"ch.threema.client.",
		"ch.threema.storage.",
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
	 *
	 * By default, it is disabled.
	 *
	 * When disabling the logging, then the file is deleted if it already exists.
	 */
	public synchronized static void setEnabled(boolean enabled) {
		DebugLogFileBackend.enabled = enabled;
		if (!enabled) {
			final File file = getLogFile();
			if (file == null) {
				Log.e(TAG,"DebugLogFileBackend: Could not get debug log file path");
				return;
			}
			if (file.exists() && !file.delete()) {
				Log.e(TAG,"DebugLogFileBackend: Could not delete debug log file");
			}
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
	static File getLogFilePath() {
		final File threemaDir = new File(ThreemaApplication.getAppContext().getExternalFilesDir(null), "log");
		return new File(threemaDir, LOGFILE_NAME);
	}

	/**
	 * Return a `File` instance pointing to the debug log file.
	 *
	 * Returns `null` if the log file directory could not be created.
	 */
	@Nullable
	private static File getLogFile() {
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
	}

	/**
	 * If the logger is enabled, write the log asynchronously to the log file.
	 *
	 * I/O is dispatched to the handler thread.
	 *
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
			// Get log file
			final File logFile = getLogFile();
			if (logFile == null) {
				Log.w(TAG, "DebugLogFileBackend: Could not get log file path");
				future.complete(false);
				return;
			}

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
			try (
				final FileWriter fw = new FileWriter(logFile, true);
				final PrintWriter pw = new PrintWriter(fw)
			) {
				pw.println(logLine);
				future.complete(true);
			} catch (Exception e) {
				// Write failed...
				future.complete(false);
			}
		});

		return future;
	}

	/**
	 * If the logger is enabled, write the log to the log file.
	 *
	 * Note: I/O is done asynchronously, so the log may not yet be fully written
	 * to storage when this method returns!
	 *
	 * @param level The log level
	 * @param tag The log tag
	 * @param throwable A throwable (may be null)
	 * @param message A message (may be null)
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
	public static File getZipFile(FileService fileService) {
		// Open log file
		final File logFile = getLogFile();
		if (logFile == null) {
			Log.w(TAG, "DebugLogFileBackend: getLogFile returned null");
			return null;
		}

		// Delete old debug log archive
		final File tempDebugLogArchive = new File(fileService.getExtTmpPath(), "debug_log.zip");
		if (tempDebugLogArchive.exists() && !tempDebugLogArchive.delete()) {
			Log.w(TAG, "DebugLogFileBackend: Could not delete tempDebugLogArchive");
		}

		// Create and return ZIP
		try (
			final FileInputStream inputStream = new FileInputStream(logFile);
			final ZipOutputStream zipOutputStream = ZipUtil.initializeZipOutputStream(tempDebugLogArchive, null)
		) {
			final ZipParameters parameters = createZipParameters(logFile.getName());
			zipOutputStream.putNextEntry(parameters);

			final byte[] buf = new byte[16384];
			int nread;
			while ((nread = inputStream.read(buf)) > 0) {
				zipOutputStream.write(buf, 0, nread);
			}

			zipOutputStream.closeEntry();

			return tempDebugLogArchive;
		} catch (Exception e) {
			return null;
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
