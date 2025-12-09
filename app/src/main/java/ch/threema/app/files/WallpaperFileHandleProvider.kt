/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.files

import android.provider.MediaStore.MEDIA_IGNORE_FILENAME
import ch.threema.common.clearDirectoryRecursively
import ch.threema.common.files.FileHandle
import ch.threema.domain.types.ConversationUniqueId
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

    fun get(uniqueId: ConversationUniqueId): FileHandle {
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
