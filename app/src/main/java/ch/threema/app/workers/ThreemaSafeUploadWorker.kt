/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import java.util.*
import java.util.concurrent.TimeUnit

class ThreemaSafeUploadWorker(context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("ThreemaSafeUploadWorker")

    private val serviceManager: ServiceManager? = ThreemaApplication.getServiceManager()
    private val threemaSafeService: ThreemaSafeService? = serviceManager?.threemaSafeService
    private val preferenceService: PreferenceService? = serviceManager?.preferenceService

    companion object {
        private const val EXTRA_FORCE_UPDATE = "FORCE_UPDATE"

        /**
         * Build a one time work request without any initial delay.
         */
        fun buildOneTimeWorkRequest(forceUpdate: Boolean): OneTimeWorkRequest {
            val data = Data.Builder()
                .putBoolean(EXTRA_FORCE_UPDATE, forceUpdate)
                .build()

            return OneTimeWorkRequestBuilder<ThreemaSafeUploadWorker>()
                .apply { setInputData(data) }
                .build()
        }

        /**
         * Build a periodic work request that runs every [schedulePeriodMs] milliseconds. The
         * request is scheduled to first run in [schedulePeriodMs] milliseconds. Note that the
         * schedule period is not added as tag, as these period does not change dynamically.
         */
        fun buildPeriodicWorkRequest(schedulePeriodMs: Long): PeriodicWorkRequest {
            val data = Data.Builder()
                .putBoolean(EXTRA_FORCE_UPDATE, false)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<ThreemaSafeUploadWorker>(
                schedulePeriodMs,
                TimeUnit.MILLISECONDS
            )
                .setInitialDelay(schedulePeriodMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(schedulePeriodMs.toString())
                .apply { setInputData(data) }
                .build()
        }
    }

    override fun doWork(): Result {
        val forceUpdate: Boolean = inputData.getBoolean(EXTRA_FORCE_UPDATE, false)
        var success = true

        logger.info("Threema Safe upload worker started, force = {}", forceUpdate)

        if (serviceManager == null || threemaSafeService == null || preferenceService == null) {
            logger.info("Services not available")
            return Result.failure()
        }

        if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
            // skip upload if license was revoked or is temporarily unavailable
            return Result.success()
        }

        try {
            threemaSafeService.createBackup(forceUpdate)
            // When the backup has been successfully uploaded or does not need to be uploaded, then
            // we ignore previous errors.
            preferenceService.threemaSafeErrorDate = null
        } catch (e: ThreemaSafeService.ThreemaSafeUploadException) {
            if (preferenceService.threemaSafeErrorDate == null && e.isUploadNeeded) {
                preferenceService.threemaSafeErrorDate = Date()
            }
            showWarningNotification()
            logger.error("Threema Safe upload failed", e)
            success = false
        }

        ListenerManager.threemaSafeListeners.handle { obj: ThreemaSafeListener -> obj.onBackupStatusChanged() }

        logger.info("Threema Safe upload worker finished. Success = {}", success)

        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun showWarningNotification() {
        val errorDate = preferenceService!!.threemaSafeErrorDate
        val aWeekAgo = Date(System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)
        if (errorDate != null && errorDate.before(aWeekAgo)) {
            val lastBackupDate = preferenceService.threemaSafeBackupDate
            val notificationService = serviceManager!!.notificationService
            val fullDaysSinceLastBackup =
                ((System.currentTimeMillis() - lastBackupDate.time) / DateUtils.DAY_IN_MILLIS).toInt();
            if (fullDaysSinceLastBackup > 0 && preferenceService.getThreemaSafeEnabled()) {
                notificationService.showSafeBackupFailed(fullDaysSinceLastBackup)
            } else {
                notificationService.cancelSafeBackupFailed()
            }
        }
    }
}
