package ch.threema.app.files

import android.provider.MediaStore.MEDIA_IGNORE_FILENAME
import ch.threema.common.clearDirectoryRecursively
import ch.threema.common.files.FileHandle
import ch.threema.domain.types.ConversationUID
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File
import java.io.IOException

class WallpaperFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun getGlobal(): FileHandle =
        appDirectoryProvider.userFilesDirectory.fileHandle(GLOBAL_WALLPAPER_FILENAME)
            .withFallback(appDirectoryProvider.legacyUserFilesDirectory.fileHandle(GLOBAL_WALLPAPER_FILENAME))
            .withEncryption(masterKeyProvider)

    fun get(uniqueId: ConversationUID): FileHandle {
        val fileName = ".w-$uniqueId"
        return appDirectoryProvider.userFilesDirectory.fileHandle(
            directory = WALLPAPERS_DIRECTORY,
            name = fileName,
        )
            .withFallback(
                appDirectoryProvider.legacyUserFilesDirectory.fileHandle(
                    directory = LEGACY_WALLPAPERS_DIRECTORY,
                    name = fileName + MEDIA_IGNORE_FILENAME,
                ),
            )
            .withEncryption(masterKeyProvider)
    }

    @Throws(IOException::class)
    fun deleteAll() {
        getGlobal().delete()
        File(appDirectoryProvider.userFilesDirectory, WALLPAPERS_DIRECTORY).clearDirectoryRecursively()
        File(appDirectoryProvider.legacyUserFilesDirectory, LEGACY_WALLPAPERS_DIRECTORY).clearDirectoryRecursively()
    }

    companion object {
        private const val WALLPAPERS_DIRECTORY = ".wallpapers"
        private const val LEGACY_WALLPAPERS_DIRECTORY = ".wallpaper"
        private const val GLOBAL_WALLPAPER_FILENAME = "wallpaper.jpg"
    }
}
