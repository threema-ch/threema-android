/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

import android.util.Log;

import org.slf4j.helpers.MessageFormatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.logging.LogLevel;
import ch.threema.logging.LoggingUtil;

/**
 * A logging backend that logs to the ADB logcat.
 */
public class LogcatBackend implements LogBackend {
	private final static String TAG = "3ma";
	private final @LogLevel int minLogLevel;

	// For tags starting with these prefixes, the package path is stripped
	private final static String[] STRIP_PREFIXES = {
		"ch.threema.app.",
		"ch.threema.client.",
		"ch.threema.storage.",
	};

	public LogcatBackend(@LogLevel int minLogLevel) {
		this.minLogLevel = minLogLevel;
	}

	@Override
	public boolean isEnabled(int level) {
		return level >= this.minLogLevel;
	}

	@Override
	public void print(
		@LogLevel int level,
		@NonNull String tag,
		@Nullable Throwable throwable,
		@Nullable String message
	) {
		if (this.isEnabled(level)) {
			// Prepend tag to message body to avoid the Android log tag length limit
			String messageBody = LoggingUtil.cleanTag(tag, STRIP_PREFIXES) + ": ";
			if (message == null) {
				if (throwable == null) {
					messageBody += "";
				} else {
					messageBody += Log.getStackTraceString(throwable);
				}
			} else {
				if (throwable == null) {
					messageBody += message;
				} else {
					messageBody += message + '\n' + Log.getStackTraceString(throwable);
				}
			}
			Log.println(level, TAG, messageBody);
		}
	}

	@Override
	public void print(
		@LogLevel int level,
		@NonNull String tag,
		@Nullable Throwable throwable,
		@NonNull String messageFormat,
		Object... args
	) {
		if (this.isEnabled(level)) {
			try {
				this.print(level, tag, throwable, MessageFormatter.arrayFormat(messageFormat, args).getMessage());
			} catch (Exception e) { // Never crash
				this.print(level, tag, throwable, messageFormat);
			}
		}
	}

}
