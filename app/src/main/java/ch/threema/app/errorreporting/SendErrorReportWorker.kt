package ch.threema.app.errorreporting

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setBackoffCriteria
import ch.threema.android.setConstraints
import ch.threema.app.workers.WorkerNames
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("SendErrorReportWorker")

class SendErrorReportWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val errorRecordStore: ErrorRecordStore by inject()
    private val sentryService: SentryService by inject()
    private val timeProvider: TimeProvider by inject()

    override suspend fun doWork(): Result {
        errorRecordStore.getConfirmedRecords().forEach { errorRecord ->
            if (timeProvider.get() - errorRecord.createdAt < MAX_AGE) {
                try {
                    logger.info("Sending error record {} to Sentry", errorRecord.id)
                    sentryService.sendErrorRecord(errorRecord)
                } catch (e: IOException) {
                    logger.warn("Failed to send error record, retry later", e)
                    return Result.retry()
                }
            }
            errorRecordStore.deleteConfirmedRecord(errorRecord.id)
        }
        return Result.success()
    }

    class Scheduler(
        private val workManager: WorkManager,
    ) {
        fun schedule() {
            workManager.enqueueUniqueWork(
                uniqueWorkName = WorkerNames.WORKER_SEND_ERROR_REPORT,
                existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                request = buildOneTimeWorkRequest<SendErrorReportWorker> {
                    setConstraints {
                        setRequiredNetworkType(NetworkType.CONNECTED)
                    }
                    setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 5.minutes,
                    )
                },
            )
        }
    }

    companion object {
        /**
         * If an error report could not be successfully sent for this long, we just discard it to avoid retrying forever.
         * The exact value was selected somewhat arbitrarily.
         */
        private val MAX_AGE = 3.days
    }
}
