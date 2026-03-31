package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion115")

class SystemUpdateToVersion115 : SystemUpdate {
    private val appContext: Context by inject()

    override fun run() {
        try {
            // Deletes the directory that was already deleted in SystemUpdateToVersion63, but might have been recreated afterwards
            File(appContext.getExternalFilesDir(null), "tmp").deleteRecursively()
        } catch (e: Exception) {
            // If deletion fails, we just continue, as the directory might generally not be accessible anymore
            logger.error("Failed to delete external tmp directory", e)
        }
    }

    override val version = 115

    override fun getDescription() = "delete obsolete external tmp directory"
}
