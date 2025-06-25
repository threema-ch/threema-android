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

package ch.threema.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.core.content.getSystemService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.utils.ApplicationExitInfoUtil.getReasonText
import ch.threema.app.utils.ApplicationExitInfoUtil.getStatusText
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.logging.backend.DebugLogFileBackend
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

private val logger = LoggingUtil.getThreemaLogger("AppLogging")

object AppLogging {

    fun disableIfNeeded(context: Context, preferenceStore: PreferenceStore) {
        if (!isDebugLogPreferenceEnabled(context, preferenceStore) && !forceDebugLog()) {
            DebugLogFileBackend.setEnabled(false)
        }
    }

    private fun isDebugLogPreferenceEnabled(context: Context, preferenceStore: PreferenceStore) =
        preferenceStore.getBoolean(context.getString(R.string.preferences__message_log_switch))

    private fun forceDebugLog(): Boolean {
        val externalStorageDirectory = Environment.getExternalStorageDirectory()
        val forceDebugLogFile = File(externalStorageDirectory, "ENABLE_THREEMA_DEBUG_LOG")
        return forceDebugLogFile.exists()
    }

    @JvmStatic
    fun logAppVersionInfo(context: Context) {
        val buildInfo = buildString {
            append(ConfigUtils.getBuildNumber(context))
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

    fun logExitReason(context: Context, sharedPreferences: SharedPreferences?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val activityManager = context.getSystemService<ActivityManager>() ?: return
        try {
            val applicationExitInfos = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
            if (applicationExitInfos.isEmpty()) {
                return
            }

            val simpleDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            for (exitInfo in applicationExitInfos) {
                val timestampOfLastLog = sharedPreferences?.getLong(EXIT_REASON_LOGGING_TIMESTAMP, 0L) ?: 0L

                if (exitInfo.timestamp > timestampOfLastLog) {
                    logger.info(
                        "*** App last exited at {} with reason: {}, description: {}, status: {}",
                        simpleDateFormat.format(exitInfo.timestamp),
                        getReasonText(exitInfo),
                        exitInfo.description,
                        getStatusText(exitInfo),
                    )
                    if (exitInfo.reason == ApplicationExitInfo.REASON_ANR) {
                        try {
                            exitInfo.traceInputStream?.use { traceInputStream ->
                                val logMessage = BufferedReader(InputStreamReader(traceInputStream)).use { reader ->
                                    reader.readText()
                                }
                                logger.info(logMessage)
                            }
                        } catch (e: IOException) {
                            logger.error("Error getting ANR trace", e)
                        }
                    }
                }
            }

            sharedPreferences?.edit {
                putLong(EXIT_REASON_LOGGING_TIMESTAMP, System.currentTimeMillis())
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Unable to get reason of last exit", e)
        }
    }

    private const val EXIT_REASON_LOGGING_TIMESTAMP = "exit_reason_timestamp"
}
