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

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

private val logger = getThreemaLogger("ExitReasonLogger")

class ExitReasonLogger(
    private val appContext: Context,
    private val sharedPreferences: SharedPreferences,
    private val timeProvider: TimeProvider,
) {
    fun logExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val activityManager = appContext.getSystemService<ActivityManager>() ?: return
        try {
            val applicationExitInfos = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
            if (applicationExitInfos.isEmpty()) {
                return
            }

            val simpleDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            for (exitInfo in applicationExitInfos) {
                val timestampOfLastLog = sharedPreferences.getLong(EXIT_REASON_LOGGING_TIMESTAMP, 0L)

                if (exitInfo.timestamp > timestampOfLastLog) {
                    logger.info(
                        "*** App last exited at {} with reason: {}, description: {}, status: {}",
                        simpleDateFormat.format(exitInfo.timestamp),
                        exitInfo.getReasonText(),
                        exitInfo.description,
                        exitInfo.getStatusText(),
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

            sharedPreferences.edit {
                putLong(EXIT_REASON_LOGGING_TIMESTAMP, timeProvider.get().toEpochMilli())
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Unable to get reason of last exit", e)
        }
    }

    companion object {
        private const val EXIT_REASON_LOGGING_TIMESTAMP = "exit_reason_timestamp"

        @RequiresApi(Build.VERSION_CODES.R)
        private fun ApplicationExitInfo.getStatusText(): String =
            when (status) {
                OsConstants.SIGABRT -> "SIGABRT"
                OsConstants.SIGALRM -> "SIGALRM"
                OsConstants.SIGBUS -> "SIGBUS"
                OsConstants.SIGCHLD -> "SIGCHLD"
                OsConstants.SIGCONT -> "SIGCONT"
                OsConstants.SIGFPE -> "SIGFPE"
                OsConstants.SIGHUP -> "SIGHUP"
                OsConstants.SIGILL -> "SIGILL"
                OsConstants.SIGINT -> "SIGINT"
                OsConstants.SIGIO -> "SIGIO"
                OsConstants.SIGKILL -> "SIGKILL"
                OsConstants.SIGPIPE -> "SIGPIPE"
                OsConstants.SIGPROF -> "SIGPROF"
                OsConstants.SIGPWR -> "SIGPWR"
                OsConstants.SIGQUIT -> "SIGQUIT"
                OsConstants.SIGRTMAX -> "SIGRTMAX"
                OsConstants.SIGRTMIN -> "SIGRTMIN"
                OsConstants.SIGSEGV -> "SIGSEGV"
                OsConstants.SIGSTKFLT -> "SIGSTKFLT"
                OsConstants.SIGSTOP -> "SIGSTOP"
                OsConstants.SIGSYS -> "SIGSYS"
                OsConstants.SIGTERM -> "SIGTERM"
                OsConstants.SIGTRAP -> "SIGTRAP"
                OsConstants.SIGTSTP -> "SIGTSTP"
                OsConstants.SIGTTIN -> "SIGTTIN"
                OsConstants.SIGTTOU -> "SIGTTOU"
                OsConstants.SIGURG -> "SIGURG"
                OsConstants.SIGUSR1 -> "SIGUSR1"
                OsConstants.SIGUSR2 -> "SIGUSR2"
                OsConstants.SIGVTALRM -> "SIGVTALRM"
                OsConstants.SIGWINCH -> "SIGWINCH"
                OsConstants.SIGXCPU -> "SIGXCPU"
                OsConstants.SIGXFSZ -> "SIGXFSZ"
                0 -> null
                else -> "UNKNOWN"
            }
                ?.let { "$status ($it)" }
                ?: "$status"

        @RequiresApi(Build.VERSION_CODES.R)
        private fun ApplicationExitInfo.getReasonText(): String {
            val reasonName = when (reason) {
                ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                ApplicationExitInfo.REASON_CRASH -> "APP CRASH(EXCEPTION)"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "APP CRASH(NATIVE)"
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION FAILURE"
                ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION CHANGE"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE RESOURCE USAGE"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "USER REQUESTED"
                ApplicationExitInfo.REASON_USER_STOPPED -> "USER STOPPED"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY DIED"
                ApplicationExitInfo.REASON_OTHER -> "OTHER KILLS BY SYSTEM"
                ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
                ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "STATE CHANGE"
                ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE UPDATED"
                else -> "UNKNOWN"
            }
            return "$reason ($reasonName)"
        }
    }
}
