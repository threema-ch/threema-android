package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion125")

class SystemUpdateToVersion125 : SystemUpdate {
    private val appContext: Context by inject()

    override fun run() {
        val internalTempDirectory = File(appContext.filesDir, "tmp")
        try {
            if (internalTempDirectory.exists()) {
                internalTempDirectory.deleteRecursively()
            }
        } catch (e: Exception) {
            logger.error("Failed to delete internal temp directory", e)
        }
    }

    override val version = 125

    override fun getDescription() = "delete obsolete internal temp directory"
}
