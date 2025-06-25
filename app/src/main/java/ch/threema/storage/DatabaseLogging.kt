/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.storage

import android.util.Log
import ch.threema.base.utils.LoggingUtil
import net.zetetic.database.LogTarget
import net.zetetic.database.Logger

private val logger = LoggingUtil.getThreemaLogger("DatabaseLogging")

fun setupDatabaseLogging() {
    Logger.setTarget(object : LogTarget {
        override fun isLoggable(tag: String?, priority: Int) = Log.isLoggable(tag, priority)

        override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
            val fullMessage = "$tag: $message"
            when (priority) {
                Logger.VERBOSE -> logger.trace(fullMessage, throwable)
                Logger.DEBUG -> logger.debug(fullMessage, throwable)
                Logger.INFO -> logger.info(fullMessage, throwable)
                Logger.WARN -> logger.warn(fullMessage, throwable)
                Logger.ERROR -> logger.error(fullMessage, throwable)
                Logger.ASSERT -> logger.error(fullMessage, throwable)
            }
        }
    })
}
