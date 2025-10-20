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

package ch.threema.app.di

import android.annotation.SuppressLint
import ch.threema.base.utils.LoggingUtil
import org.koin.core.logger.Level
import org.koin.core.logger.Logger as KoinLogger

@SuppressLint("LoggerName")
private val logger = LoggingUtil.getThreemaLogger("Koin")

object ThreemaKoinLogger : KoinLogger() {
    override fun display(level: Level, msg: String) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.WARNING -> logger.warn(msg)
            Level.ERROR -> logger.error(msg)
            Level.NONE -> Unit
        }
    }
}
