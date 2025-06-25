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

package ch.threema.app.services

import android.content.Context
import java.io.File

class AppDirectoryProvider(
    private val context: Context,
) {
    /**
     * Secondary storage directory for files that do not need any security enforced such as encrypted media
     */
    val appDataDirectory: File by lazy {
        createIfNeeded(
            File(context.getExternalFilesDir(null), "data"),
        )
    }

    val internalTempDirectory: File
        get() = createIfNeeded(File(context.filesDir, "tmp"))

    val externalTempDirectory: File
        get() = File(context.getExternalFilesDir(null), "tmp")

    private fun createIfNeeded(directory: File): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
}
