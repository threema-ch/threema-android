package ch.threema.app.logging

import android.content.Context
import android.os.Environment
import ch.threema.app.R
import ch.threema.app.stores.PreferenceStore
import ch.threema.logging.backend.DebugLogFileBackend
import java.io.File

class DebugLogHelper(
    private val appContext: Context,
    private val preferenceStore: PreferenceStore,
) {
    fun disableDebugLogFileIfNeeded() {
        if (!isDebugLogPreferenceEnabled() && !isDebugLogFileForceEnabled()) {
            DebugLogFileBackend.setEnabled(false)
        }
    }

    private fun isDebugLogPreferenceEnabled() =
        preferenceStore.getBoolean(appContext.getString(R.string.preferences__message_log_switch))

    private fun isDebugLogFileForceEnabled(): Boolean {
        val externalStorageDirectory = Environment.getExternalStorageDirectory()
        val forceDebugLogFile = File(externalStorageDirectory, "ENABLE_THREEMA_DEBUG_LOG")
        return forceDebugLogFile.exists()
    }
}
