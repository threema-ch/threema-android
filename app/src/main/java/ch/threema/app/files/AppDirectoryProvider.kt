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
