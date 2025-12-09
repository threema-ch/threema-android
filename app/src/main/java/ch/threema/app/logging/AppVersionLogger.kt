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

package ch.threema.app.logging

import android.content.Context
import ch.threema.app.BuildConfig
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("AppVersionLogger")

class AppVersionLogger(
    private val appContext: Context,
    private val appVersionHistoryManager: AppVersionHistoryManager,
) {
    fun logAppVersionInfo() {
        val buildInfo = buildString {
            append(ConfigUtils.getBuildNumber(appContext))
            if (BuildConfig.DEBUG) {
                append(", Commit: ")
                append(BuildConfig.GIT_HASH)
            }
        }
        logger.info(
            "*** App Version. Device/Android Version/Flavor: {} Version: {} Build: {}",
            ConfigUtils.getDeviceInfo(false),
            BuildConfig.VERSION_NAME,
            buildInfo,
        )
    }

    fun logAppVersionHistory() {
        val history = appVersionHistoryManager.getHistory()
            .joinToString(separator = "\n") { record -> "→ ${record.toLogString()}" }
        logger.info("App Version History:\n{}", history)
    }

    fun updateAppVersionHistory() {
        when (val result = appVersionHistoryManager.check()) {
            AppVersionCheckResult.SameVersion -> return
            AppVersionCheckResult.NoPreviousVersion -> {
                logger.info("*** Fresh installation detected, or app version history is empty")
                appVersionHistoryManager.record()
            }
            is AppVersionCheckResult.DifferentVersion -> {
                logger.info("*** App update detected, previous version was ${result.previous.toLogString()}")
                appVersionHistoryManager.record()
            }
        }
    }

    private fun AppVersionRecord.toLogString() =
        "[$versionName ($versionCode) first opened at $time]"
}
