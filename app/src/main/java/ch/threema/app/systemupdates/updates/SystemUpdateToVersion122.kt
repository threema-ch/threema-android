package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion122")

/**
 * Renames existing debug log files, such that they are considered as valid log files by [ch.threema.logging.backend.DebugLogFileManager].
 */
class SystemUpdateToVersion122 : SystemUpdate, KoinComponent {

    private val appContext: Context by inject()

    override fun run() {
        try {
            val logDirectory = File(appContext.getExternalFilesDir(null), "log")
            val oldLogFile = File(logDirectory, "debug_log.txt")
            if (oldLogFile.exists()) {
                val newLogFile = File(logDirectory, "debug_log_2025_00.txt")
                if (!oldLogFile.renameTo(newLogFile)) {
                    oldLogFile.delete()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to migrate debug log file", e)
        }

        try {
            val fallbackLogDirectory = appContext.filesDir
            val oldFallbackLogFile = File(fallbackLogDirectory, "fallback_debug_log.txt")
            if (oldFallbackLogFile.exists()) {
                val newFallbackLogFile = File(fallbackLogDirectory, "debug_log_2025_00.txt")
                if (!oldFallbackLogFile.renameTo(newFallbackLogFile)) {
                    oldFallbackLogFile.delete()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to migrate fallback debug log file", e)
        }
    }

    override val version = 122

    override fun getDescription() = "rename debug log files"
}
