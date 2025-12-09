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

package ch.threema.logging.backend

import ch.threema.logging.LogLevel

interface LogBackend {
    /**
     * Return whether this backend is enabled for the specified log level.
     */
    fun isEnabled(@LogLevel level: Int): Boolean

    /**
     * Log a message to the backend.
     *
     * This method should automatically check using [isEnabled] if the message is allowed to be logged or not.
     *
     * @param level     The log level
     * @param tag       The log tag
     * @param throwable A throwable
     * @param message   A message
     */
    fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, message: String?)

    /**
     * Log a message to the backend.
     *
     * This method should automatically check using [isEnabled] if the message is allowed to be logged or not.
     *
     * @param level         The log level
     * @param tag           The log tag
     * @param throwable     A throwable
     * @param messageFormat A message format string
     * @param args          The message arguments
     */
    fun print(@LogLevel level: Int, tag: String, throwable: Throwable?, messageFormat: String, vararg args: Any?)
}
