/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import org.slf4j.helpers.MarkerIgnoringBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.logging.backend.LogBackend;

/**
 * The Threema logger. It logs all messages using the registered backend(s).
 */
@SuppressWarnings("unused")
public class ThreemaLogger extends MarkerIgnoringBase {
	@NonNull
	private final String tag;
	@NonNull
	private final List<LogBackend> backends = new ArrayList<>();
	@Nullable
	private String prefix = null;


	// Constructors

	ThreemaLogger(@NonNull String logTag, @NonNull LogBackend backend) {
		this.tag = logTag;
		this.backends.add(backend);
	}
	ThreemaLogger(@NonNull String logTag, @NonNull LogBackend[] backends) {
		this.tag = logTag;
		this.backends.addAll(Arrays.asList(backends));
	}
	ThreemaLogger(@NonNull String logTag, @NonNull List<LogBackend> backends) {
		this.tag = logTag;
		this.backends.addAll(backends);
	}


	// Delegate logging to backends

	public void print(@LogLevel int level, @Nullable Throwable throwable, @Nullable String message) {
		if (this.prefix != null) {
			message = this.prefix + ": " + message;
		}
		for (LogBackend backend : this.backends) {
			backend.print(level, this.tag, throwable, message);
		}
	}
	public void print(@LogLevel int level, @Nullable Throwable throwable, @NonNull String messageFormat, Object... args) {
		Throwable extractedThrowable = throwable != null ? throwable : getThrowableCandidate(args);

		if (this.prefix != null) {
			messageFormat = this.prefix + ": " + messageFormat;
		}
		for (LogBackend backend : this.backends) {
			backend.print(level, this.tag, extractedThrowable, messageFormat, args);
		}
	}


	// Set prefix if desired

	public void setPrefix(@Nullable String prefix) {
		this.prefix = prefix;
	}


	// Log levels

	@Override
	public boolean isTraceEnabled() {
		return true;
	}
	@Override
	public boolean isDebugEnabled() {
		return true;
	}
	@Override
	public boolean isInfoEnabled() {
		return true;
	}
	@Override
	public boolean isWarnEnabled() {
		return true;
	}
	@Override
	public boolean isErrorEnabled() {
		return true;
	}


	// Logging calls

	@Override
	public void trace(String msg) {
		this.print(Log.VERBOSE, null, msg);
	}
	@Override
	public void trace(String format, Object arg) {
		this.print(Log.VERBOSE, null, format, arg);
	}
	@Override
	public void trace(String format, Object arg1, Object arg2) {
		this.print(Log.VERBOSE, null, format, arg1, arg2);
	}
	@Override
	public void trace(String format, Object... arguments) {
		this.print(Log.VERBOSE, null, format, arguments);
	}
	@Override
	public void trace(String msg, Throwable t) {
		this.print(Log.VERBOSE, t, msg);
	}

	@Override
	public void debug(String msg) {
		this.print(Log.DEBUG, null, msg);
	}
	@Override
	public void debug(String format, Object arg) {
		this.print(Log.DEBUG, null, format, arg);
	}
	@Override
	public void debug(String format, Object arg1, Object arg2) {
		this.print(Log.DEBUG, null, format, arg1, arg2);
	}
	@Override
	public void debug(String format, Object... arguments) {
		this.print(Log.DEBUG, null, format, arguments);
	}
	@Override
	public void debug(String msg, Throwable t) {
		this.print(Log.DEBUG, t, msg);
	}

	@Override
	public void info(String msg) {
		this.print(Log.INFO, null, msg);
	}
	@Override
	public void info(String format, Object arg) {
		this.print(Log.INFO, null, format, arg);
	}
	@Override
	public void info(String format, Object arg1, Object arg2) {
		this.print(Log.INFO, null, format, arg1, arg2);
	}
	@Override
	public void info(String format, Object... arguments) {
		this.print(Log.INFO, null, format, arguments);
	}
	@Override
	public void info(String msg, Throwable t) {
		this.print(Log.INFO, t, msg);
	}

	@Override
	public void warn(String msg) {
		this.print(Log.WARN, null, msg);
	}
	@Override
	public void warn(String format, Object arg) {
		this.print(Log.WARN, null, format, arg);
	}
	@Override
	public void warn(String format, Object arg1, Object arg2) {
		this.print(Log.WARN, null, format, arg1, arg2);
	}
	@Override
	public void warn(String format, Object... arguments) {
		this.print(Log.WARN, null, format, arguments);
	}
	@Override
	public void warn(String msg, Throwable t) {
		this.print(Log.WARN, t, msg);
	}

	@Override
	public void error(String msg) {
		this.print(Log.ERROR, null, msg);
	}
	@Override
	public void error(String format, Object arg) {
		this.print(Log.ERROR, null, format, arg);
	}
	@Override
	public void error(String format, Object arg1, Object arg2) {
		this.print(Log.ERROR, null, format, arg1, arg2);
	}
	@Override
	public void error(String format, Object... arguments) {
		this.print(Log.ERROR, null, format, arguments);
	}
	@Override
	public void error(String msg, @Nullable Throwable t) {
		this.print(Log.ERROR, t, msg);
	}

	/**
	 * Get the throwable if the last element is a throwable.
	 *
	 * @param argArray the arguments
	 * @return the throwable if the last element is a throwable
	 */
	@Nullable
	private Throwable getThrowableCandidate(@Nullable final Object[] argArray) {
		if (argArray == null || argArray.length == 0) {
			return null;
		}

		final Object lastEntry = argArray[argArray.length - 1];
		if (lastEntry instanceof Throwable) {
			return (Throwable) lastEntry;
		}

		return null;
	}

}
