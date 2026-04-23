package ch.threema.app.files

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setInitialDelay
import ch.threema.android.setInputData
import ch.threema.app.workers.WorkerNames.WORKER_TEMP_FILES_CLEANUP
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.deleteSecurely
import ch.threema.common.isEmptyDirectory
import ch.threema.common.lastModifiedTime
import ch.threema.common.minus
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("TempFilesCleanupWorker")

class TempFilesCleanupWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val timeProvider: TimeProvider by inject()
    private val appDirectoryProvider: AppDirectoryProvider by inject()

    override suspend fun doWork(): Result {
        val ageThreshold = getAgeThreshold()
        logger.debug("Deleting temp files older than {}", ageThreshold)
        val timeThreshold = timeProvider.get() - ageThreshold
        deleteOldFiles(appDirectoryProvider.cacheDirectory, timeThreshold)
        return Result.success()
    }

    private fun deleteOldFiles(directory: File, threshold: Instant) {
        directory.walkBottomUp()
            .filter { file -> file.lastModifiedTime() < threshold }
            .filter(::canDelete)
            .forEach { file ->
                if (file.isEmptyDirectory) {
                    if (!file.delete()) {
                        logger.warn("Failed to delete temp directory {}", file)
                    }
                } else if (file.isFile) {
                    try {
                        logger.info("Deleting temp file {}", file.path)
                        file.deleteSecurely(applicationContext.filesDir)
                    } catch (e: IOException) {
                        logger.error("Failed to delete temp file", e)
                    }
                }
            }
    }

    private fun canDelete(file: File): Boolean {
        if (file.isFile && file.extension == "lck") {
            // Don't delete database lock files, such as androidx.work.workdb.lck
            return false
        }
        if (file.isDirectory && file.name.startsWith("jna-")) {
            // Don't delete the JNA cache directory
            return false
        }
        return true
    }

    private fun getAgeThreshold(): Duration =
        inputData.getLong(EXTRA_AGE_THRESHOLD, 0L).milliseconds

    companion object {
        private const val EXTRA_AGE_THRESHOLD = "ageThreshold"

        @JvmStatic
        @JvmOverloads
        fun enqueue(context: Context, fileAgeThreshold: Duration = Duration.ZERO) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORKER_TEMP_FILES_CLEANUP, ExistingWorkPolicy.APPEND, buildWorkRequest(fileAgeThreshold))
        }

        private fun buildWorkRequest(ageThreshold: Duration): OneTimeWorkRequest =
            buildOneTimeWorkRequest<TempFilesCleanupWorker> {
                setInputData {
                    putLong(EXTRA_AGE_THRESHOLD, ageThreshold.inWholeMilliseconds)
                }
                if (ageThreshold != Duration.ZERO) {
                    // Cleanup doesn't need to happen immediately
                    setInitialDelay(20.seconds)
                }
            }
    }
}
