package ch.threema.app.logging

import android.content.Context
import android.os.Environment
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.TimeProvider
import ch.threema.logging.backend.DebugLogFileBackend
import ch.threema.logging.backend.DebugLogFileManager
import java.io.File

class DebugLogHelper(
    private val appContext: Context,
    private val preferenceService: PreferenceService,
    private val debugLogFileManager: DebugLogFileManager,
    private val appVersionLogger: AppVersionLogger,
    private val timeProvider: TimeProvider,
) {
    fun disableDebugLogFileLoggingIfNeeded() {
        if (!isDebugFileLoggingEnabled()) {
            DebugLogFileBackend.setEnabled(debugLogFileManager, false)
        }
    }

    private fun isDebugFileLoggingEnabled() =
        preferenceService.isDebugLogEnabled() || isDebugLogFileLoggingForceEnabled()

    fun isDebugLogFileLoggingForceEnabled(): Boolean {
        val externalStorageDirectory = Environment.getExternalStorageDirectory()
        val forceDebugLogFile = File(externalStorageDirectory, FORCE_ENABLE_FILE_NAME)
        return forceDebugLogFile.exists()
    }

    fun setEnabled(enabled: Boolean) {
        DebugLogFileBackend.setEnabled(debugLogFileManager, enabled)
        if (enabled) {
            preferenceService.setDebugLogEnabledTimestamp(timeProvider.get())
            appVersionLogger.logAppVersionInfo()
        } else {
            preferenceService.setDebugLogEnabledTimestamp(null)
        }
        updateDebugLogFileDeletionSchedule()
    }

    fun updateDebugLogFileDeletionSchedule() {
        if (isDebugFileLoggingEnabled()) {
            DebugLogFileCleanupWorker.schedule(appContext)
        } else {
            DebugLogFileCleanupWorker.cancel(appContext)
        }
    }

    companion object {
        const val FORCE_ENABLE_FILE_NAME = "ENABLE_THREEMA_DEBUG_LOG"
    }
}
