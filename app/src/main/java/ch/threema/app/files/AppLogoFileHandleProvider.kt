package ch.threema.app.files

import ch.threema.common.files.FileHandle

class AppLogoFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
) {
    fun get(theme: Theme): FileHandle {
        val key = when (theme) {
            Theme.LIGHT -> "light"
            Theme.DARK -> "dark"
        }
        return appDirectoryProvider.appDataDirectory.fileHandle("app_logo_$key.png")
            .withFallback(appDirectoryProvider.legacyUserFilesDirectory.fileHandle("appicon_$key.png"))
    }

    enum class Theme {
        LIGHT,
        DARK,
    }
}
