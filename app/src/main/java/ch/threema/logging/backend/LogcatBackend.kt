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

import android.util.Log
import ch.threema.app.BuildConfig
import ch.threema.logging.LogLevel
import org.slf4j.helpers.MessageFormatter

/**
 * A logging backend that logs to the ADB logcat.
 */
class LogcatBackend(@field:LogLevel @param:LogLevel private val minLogLevel: Int) : LogBackend {
    override fun isEnabled(level: Int): Boolean {
        return level >= this.minLogLevel
    }

    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ) {
        if (!isEnabled(level)) {
            return
        }
        // Prepend tag to message body to avoid the Android log tag length limit
        var messageBody = cleanTag(tag, STRIP_PREFIXES) + ": "
        if (message == null) {
            if (throwable != null) {
                messageBody += throwable.stackTraceToString()
            }
        } else {
            messageBody += message
            if (throwable != null) {
                messageBody += '\n' + throwable.stackTraceToString()
            }
        }
        Log.println(level, TAG, messageBody)
    }

    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        messageFormat: String,
        vararg args: Any?,
    ) {
        if (!isEnabled(level)) {
            return
        }
        try {
            print(level, tag, throwable, message = MessageFormatter.arrayFormat(messageFormat, args).message)
        } catch (_: Exception) {
            print(level, tag, throwable, message = messageFormat)
        }
    }

    companion object {
        private const val TAG = BuildConfig.LOG_TAG

        /**
         * For tags starting with these prefixes, the package path is stripped
         */
        private val STRIP_PREFIXES = arrayOf(
            "ch.threema.app.",
            "ch.threema.domain.",
            "ch.threema.storage.",
        )
    }
}
