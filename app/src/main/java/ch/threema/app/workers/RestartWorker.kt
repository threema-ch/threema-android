package ch.threema.app.workers

import android.content.Context
import android.content.Intent
import androidx.work.*
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.TimeUnit

private val logger = getThreemaLogger("RestartWorker")

class RestartWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        logger.info("Restarting the app")
        val restartIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)!!
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(restartIntent)
        return Result.success()
    }

    companion object {
        fun buildOneTimeWorkRequest(delayMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<RestartWorker>()
                .apply {
                    if (delayMs > 0) {
                        setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    }
                }
                .build()
    }
}
