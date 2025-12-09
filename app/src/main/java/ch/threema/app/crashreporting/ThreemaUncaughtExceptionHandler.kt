/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.crashreporting

import android.content.Context
import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator

private val logger = getThreemaLogger("ThreemaUncaughtExceptionHandler")

class ThreemaUncaughtExceptionHandler(
    private val appContext: Context,
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.error("Uncaught exception", e)

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.CRASH_REPORTING_SUPPORTED) {
            storeExceptionForCrashReporting(e)
        }

        defaultHandler?.uncaughtException(t, e)
    }

    private fun storeExceptionForCrashReporting(e: Throwable) {
        // Intentionally not using Koin here, as it might not be initialized yet at this point
        ExceptionRecordStore(
            recordsDirectory = ExceptionRecordStore.getRecordsDirectory(appContext),
            timeProvider = TimeProvider.default,
            uuidGenerator = UUIDGenerator.default,
        )
            .storeException(e)
    }
}
