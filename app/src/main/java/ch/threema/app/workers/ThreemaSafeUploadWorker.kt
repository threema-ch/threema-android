package ch.threema.app.workers

import android.content.Context
import androidx.work.*
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setConstraints
import ch.threema.android.setInitialDelay
import ch.threema.android.setInputData
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.minus
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ThreemaSafeUploadWorker")

class ThreemaSafeUploadWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val threemaSafeService: ThreemaSafeService by inject()
    private val preferenceService: PreferenceService by inject()
    private val notificationService: NotificationService by inject()

    override suspend fun doWork(): Result {
        val forceUpdate: Boolean = inputData.getBoolean(EXTRA_FORCE_UPDATE, false)
        var success = true

        logger.info("Threema Safe upload worker started, force = {}", forceUpdate)

        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.failure()

        if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
            // skip upload if license was revoked or is temporarily unavailable
            return Result.success()
        }

        try {
            threemaSafeService.createBackup(forceUpdate)
            // When the backup has been successfully uploaded or does not need to be uploaded, then
            // we ignore previous errors.
            preferenceService.setThreemaSafeErrorTimestamp(null)
        } catch (e: ThreemaSafeService.ThreemaSafeUploadException) {
            if (preferenceService.getThreemaSafeErrorTimestamp() == null && e.isUploadNeeded) {
                preferenceService.setThreemaSafeErrorTimestamp(Instant.now())
            }
            showWarningNotification(preferenceService, notificationService)
            logger.error("Threema Safe upload failed", e)
            success = false
        }

        ListenerManager.threemaSafeListeners.handle { listener -> listener.onBackupStatusChanged() }

        logger.info("Threema Safe upload worker finished. Success = {}", success)

        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun showWarningNotification(preferenceService: PreferenceService, notificationService: NotificationService) {
        val errorTimestamp = preferenceService.getThreemaSafeErrorTimestamp() ?: return
        if (errorTimestamp < Instant.now() - 7.days) {
            val lastBackupDate = preferenceService.getThreemaSafeBackupTimestamp()
            val fullDaysSinceLastBackup = lastBackupDate?.let { (Instant.now() - lastBackupDate).inWholeDays.toInt() }
            if (fullDaysSinceLastBackup != null && fullDaysSinceLastBackup > 0 && preferenceService.getThreemaSafeEnabled()) {
                notificationService.showSafeBackupFailed(fullDaysSinceLastBackup)
            } else {
                notificationService.cancelSafeBackupFailed()
            }
        }
    }

    companion object {
        private const val EXTRA_FORCE_UPDATE = "FORCE_UPDATE"

        /**
         * Build a one time work request without any initial delay.
         */
        @JvmStatic
        fun buildWorkRequest(forceUpdate: Boolean): OneTimeWorkRequest =
            buildOneTimeWorkRequest<ThreemaSafeUploadWorker> {
                setInputData {
                    putBoolean(EXTRA_FORCE_UPDATE, forceUpdate)
                }
            }

        /**
         * Build a periodic work request that runs every [schedulePeriodMs] milliseconds. The
         * request is scheduled to first run in [schedulePeriodMs] milliseconds. Note that the
         * schedule period is not added as tag, as these period does not change dynamically.
         */
        @JvmStatic
        fun buildWorkRequest(schedulePeriodMs: Long): PeriodicWorkRequest =
            buildPeriodicWorkRequest<ThreemaSafeUploadWorker>(
                repeatInterval = schedulePeriodMs.milliseconds,
            ) {
                setInitialDelay(schedulePeriodMs.milliseconds)
                setConstraints {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
                addTag(schedulePeriodMs.toString())
                setInputData {
                    putBoolean(EXTRA_FORCE_UPDATE, false)
                }
            }
    }
}
