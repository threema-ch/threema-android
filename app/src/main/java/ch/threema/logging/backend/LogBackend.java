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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.logging.LogLevel;

public interface LogBackend {
	/**
	 * Return whether this backend is enabled for the specified log level.
	 */
	boolean isEnabled(@LogLevel int level);

	/**
	 * Log a message to the backend.
	 *
	 * This method should automatically check using {@link #isEnabled(int)} method
	 * if the message is allowed to be logged or not.
	 *
	 * @param level The log level
	 * @param tag The log tag
	 * @param throwable A throwable (may be null)
	 * @param message A message (may be null)
	 */
	void print(@LogLevel int level, @NonNull String tag, @Nullable Throwable throwable, @Nullable String message);

	/**
	 * Log a message to the backend.
	 *
	 * This method should automatically check using {@link #isEnabled(int)} method
	 * if the message is allowed to be logged or not.
	 *
	 * @param level The log level
	 * @param tag The log tag
	 * @param throwable A throwable (may be null)
	 * @param messageFormat A messag eformat string
	 * @param args The message arguments
	 */
	void print(@LogLevel int level, @NonNull String tag, @Nullable Throwable throwable, @NonNull String messageFormat, Object... args);
}
