package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setBackoffCriteria
import ch.threema.app.workers.AutostartWorker
import ch.threema.app.workers.WorkerNames
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.minutes

private val logger = getThreemaLogger("AutoStartNotifyReceiver")

class AutoStartNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            logger.info("*** Phone rebooted - AutoStart")
            val workRequest = buildOneTimeWorkRequest<AutostartWorker> {
                setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1.minutes)
            }
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WorkerNames.WORKER_AUTOSTART, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
