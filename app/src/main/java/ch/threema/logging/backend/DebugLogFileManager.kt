package ch.threema.logging.backend

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = getThreemaLogger("DebugLogFileManager")

class DebugLogFileManager(
    appContext: Context,
) {
    private val logDirectory = File(appContext.getExternalFilesDir(null), "log")
    private val fallbackLogDirectory = appContext.filesDir

    fun getCurrentLogFile(now: Instant): File =
        File(logDirectory, getLogFileName(now))

    fun getCurrentFallbackLogFile(now: Instant): File =
        File(fallbackLogDirectory, getLogFileName(now))

    private fun getLogFileName(now: Instant): String =
        FILE_NAME_PREFIX + getTimestamp(now) + FILE_NAME_SUFFIX

    fun getLogFiles() =
        getLogFiles(logDirectory)

    fun getFallbackLogFiles() =
        getLogFiles(fallbackLogDirectory)

    private fun getLogFiles(directory: File): List<File> =
        directory.listFiles { file ->
            file.name.startsWith(FILE_NAME_PREFIX) && file.name.endsWith(FILE_NAME_SUFFIX)
        }
            ?.sortedBy { file -> file.name }
            ?: emptyList()

    fun createLogDirectory() {
        if (logDirectory.exists()) {
            return
        }
        if (!logDirectory.mkdirs()) {
            logger.error("Failed to create log directory")
        }
    }

    fun deleteLogFiles() {
        getLogFiles().forEach { logFile ->
            if (logFile.exists() && !logFile.delete()) {
                logger.error("Failed to delete log file {}", logFile.name)
            }
        }
    }

    fun deleteFallbackLogFiles() {
        getFallbackLogFiles().forEach { logFile ->
            if (logFile.exists() && !logFile.delete()) {
                logger.error("Failed to delete fallback log file {}", logFile.name)
            }
        }
    }

    companion object {
        private const val FILE_NAME_PREFIX = "debug_log_"
        private const val FILE_NAME_SUFFIX = ".txt"
        private val suffixFormatter = DateTimeFormatter.ofPattern("yyyy_MM")

        private fun getTimestamp(now: Instant) =
            now.atOffset(ZoneOffset.UTC).format(suffixFormatter)
    }
}
