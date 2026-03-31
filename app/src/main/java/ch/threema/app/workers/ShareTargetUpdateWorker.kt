package ch.threema.app.workers

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setConstraints
import ch.threema.android.setInitialDelay
import ch.threema.app.BuildConfig
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ShareTargetUpdateWorker")

class ShareTargetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val userService: UserService by inject()
    private val preferenceService: PreferenceService by inject()
    private val conversationService: ConversationService by inject()

    override suspend fun doWork(): Result {
        logger.info("Updating share target shortcuts")

        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.failure()

        if (userService.hasIdentity() && !BackupService.isRunning()) {
            ShortcutUtil.publishRecentChatsAsShareTargets(preferenceService, conversationService)
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
            try {
                val workRequest = buildPeriodicWorkRequest<ShareTargetUpdateWorker>(
                    repeatInterval = 15.minutes,
                ) {
                    setConstraints {
                        setRequiredNetworkType(NetworkType.CONNECTED)
                    }
                    // give the app some time to update conversations first when the app is started
                    setInitialDelay(20.seconds)
                }

                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniquePeriodicWork(WORKER_SHARE_TARGET_UPDATE, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest)
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
