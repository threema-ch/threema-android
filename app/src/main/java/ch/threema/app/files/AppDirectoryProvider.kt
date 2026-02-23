package ch.threema.app.files

import android.content.Context
import java.io.File

class AppDirectoryProvider(
    private val context: Context,
) {
    /**
     * Directory for storing the user's files, such as message file attachments, images, voice recordings, profile pictures,
     * group profile pictures, wallpapers, ...
     *
     * It is recommended that files in this directory are encrypted, though exceptions are possible.
     */
    val userFilesDirectory: File by lazy {
        createIfNeeded(
            File(context.filesDir, "user-data"),
        )
    }

    /**
     * Directory for storing app specific files, such as app meta and config data, keys, ...
     * For user files, use [userFilesDirectory] instead.
     */
    val appDataDirectory: File = context.filesDir

    /**
     * Directory formerly used for storing the user's encrypted files.
     * Only used for reading old files for backwards compatibility.
     * New files should never be stored here, as the directory may not always be accessible.
     * Use [userFilesDirectory] instead.
     */
    @Deprecated("Use only for reading old files, use userFilesDirectory instead")
    val legacyUserFilesDirectory: File
        get() = File(context.getExternalFilesDir(null), "data")

    val internalTempDirectory: File
        get() = createIfNeeded(File(context.filesDir, "tmp"))

    private fun createIfNeeded(directory: File): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
}
