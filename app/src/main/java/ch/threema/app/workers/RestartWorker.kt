package ch.threema.app.workers

import android.content.Context
import android.content.Intent
import androidx.work.*
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setInitialDelay
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.milliseconds

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
        @JvmStatic
        fun buildWorkRequest(delayMs: Long): OneTimeWorkRequest =
            buildOneTimeWorkRequest<RestartWorker> {
                if (delayMs > 0) {
                    setInitialDelay(delayMs.milliseconds)
                }
            }
    }
}
