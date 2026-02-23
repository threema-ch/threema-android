package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File

private val logger = getThreemaLogger("SystemUpdateToVersion115")

class SystemUpdateToVersion115(
    private val appContext: Context,
) : SystemUpdate {
    override fun run() {
        try {
            // Deletes the directory that was already deleted in SystemUpdateToVersion63, but might have been recreated afterwards
            File(appContext.getExternalFilesDir(null), "tmp").deleteRecursively()
        } catch (e: Exception) {
            // If deletion fails, we just continue, as the directory might generally not be accessible anymore
            logger.error("Failed to delete external tmp directory", e)
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "delete obsolete external tmp directory"

    companion object {
        const val VERSION = 115
    }
}
