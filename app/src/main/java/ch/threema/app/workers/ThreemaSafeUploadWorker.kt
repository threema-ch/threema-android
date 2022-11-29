/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.workers

import android.content.Context
import android.text.format.DateUtils
import androidx.work.*
import ch.threema.app.ThreemaApplication
import ch.threema.app.listeners.ThreemaSafeListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.PreferenceService
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import java.util.*
import java.util.concurrent.TimeUnit

class ThreemaSafeUploadWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("ThreemaSafeUploadWorker")

    private val serviceManager: ServiceManager? = ThreemaApplication.getServiceManager()
    private val threemaSafeService: ThreemaSafeService? = serviceManager?.threemaSafeService
    private val preferenceService: PreferenceService? = serviceManager?.preferenceService

    companion object {
        private const val EXTRA_FORCE_UPDATE = "FORCE_UPDATE"

        fun buildOneTimeWorkRequest(forceUpdate: Boolean): OneTimeWorkRequest {
            val data = Data.Builder()
                    .putBoolean(EXTRA_FORCE_UPDATE, forceUpdate)
                    .build()

            return OneTimeWorkRequestBuilder<ThreemaSafeUploadWorker>()
                    .apply { setInputData(data) }
                    .build()
        }

        fun buildPeriodicWorkRequest(schedulePeriodMs: Long): PeriodicWorkRequest {
            val data = Data.Builder()
                    .putBoolean(EXTRA_FORCE_UPDATE, false)
                    .build()
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            return PeriodicWorkRequestBuilder<ThreemaSafeUploadWorker>(schedulePeriodMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .apply { setInputData(data) }
                    .build()
        }
    }

    override fun doWork(): Result {
        val forceUpdate: Boolean = inputData.getBoolean(EXTRA_FORCE_UPDATE, false)
        var success = true

        logger.info("Uploading Threema Safe, force = {}", forceUpdate)

        if (serviceManager == null || threemaSafeService == null || preferenceService == null) {
            logger.info("Services not available")
            return Result.failure()
        }

        try {
            threemaSafeService.createBackup(forceUpdate)
        } catch (e: ThreemaException) {
            showWarningNotification()
            logger.error("Threema Safe upload failed", e)
            success = false
        }

        ListenerManager.threemaSafeListeners.handle { obj: ThreemaSafeListener -> obj.onBackupStatusChanged() }

        logger.info("Threema Safe upload finished. Success = {}", success)

        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun showWarningNotification() {
        val backupDate = preferenceService!!.threemaSafeBackupDate
        val aWeekAgo = Date(System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)
        if (backupDate != null && backupDate.before(aWeekAgo)) {
            val notificationService = serviceManager!!.notificationService
            notificationService?.showSafeBackupFailed(((System.currentTimeMillis() - backupDate.time) / DateUtils.DAY_IN_MILLIS).toInt())
        }
    }
}
