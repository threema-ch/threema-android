package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import ch.threema.app.workers.AutostartWorker
import ch.threema.app.workers.WorkerNames
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.TimeUnit

private val logger = getThreemaLogger("AutoStartNotifyReceiver")

class AutoStartNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            logger.info("*** Phone rebooted - AutoStart")
            val workRequest = OneTimeWorkRequest.Builder(AutostartWorker::class.java)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WorkerNames.WORKER_AUTOSTART, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
