/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.app.BuildConfig
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.di.awaitServiceManagerWithTimeout
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.WorkManagerUtil
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent

private val logger = LoggingUtil.getThreemaLogger("ShareTargetUpdateWorker")

class ShareTargetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), KoinComponent {
    override suspend fun doWork(): Result {
        logger.info("Updating share target shortcuts")

        val serviceManager = awaitServiceManagerWithTimeout(timeout = 20.seconds)
            ?: return Result.failure()

        if (serviceManager.userService.hasIdentity() && !BackupService.isRunning()) {
            ShortcutUtil.publishRecentChatsAsShareTargets()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            applicationContext.getSystemService<UsageStatsManager>()?.let { usageStatsManager ->
                logger.info(
                    "Is inactive = {}; Standby bucket = {} (should be <= 10)",
                    usageStatsManager.isAppInactive(BuildConfig.APPLICATION_ID),
                    usageStatsManager.appStandbyBucket,
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORKER_SHARE_TARGET_UPDATE = "ShareTargetUpdate"

        @WorkerThread
        fun scheduleShareTargetShortcutUpdate(context: Context): Boolean {
            logger.info("Scheduling share target shortcut update work")

            val schedulePeriod = DateUtils.MINUTE_IN_MILLIS * 15

            try {
                val workManager = WorkManager.getInstance(context)

                if (WorkManagerUtil.shouldScheduleNewWorkManagerInstance(workManager, WORKER_SHARE_TARGET_UPDATE, schedulePeriod)) {
                    logger.debug("Create new worker")

                    // schedule the start of the service according to schedule period
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val workRequest = PeriodicWorkRequest.Builder(ShareTargetUpdateWorker::class.java, schedulePeriod, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .addTag(schedulePeriod.toString())
                        .setInitialDelay(3, TimeUnit.MINUTES)
                        .build()

                    workManager.enqueueUniquePeriodicWork(WORKER_SHARE_TARGET_UPDATE, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest)
                } else {
                    logger.debug("Reusing existing worker")
                }
            } catch (e: IllegalStateException) {
                logger.error("Unable to schedule share target update work", e)
                return false
            }

            return true
        }

        fun cancelScheduledShareTargetShortcutUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORKER_SHARE_TARGET_UPDATE)
        }
    }
}
