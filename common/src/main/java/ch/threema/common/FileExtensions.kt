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

package ch.threema.common

import ch.threema.common.files.FileHandle
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Deletes all files and directories inside a directory without deleting the directory itself.
 */
fun File.clearDirectoryRecursively() {
    listFiles()
        ?.forEach { file ->
            file.deleteRecursively()
        }
}

/**
 * Deletes all files inside a directory but leaves directories and their contents, and does not delete the directory itself.
 */
fun File.clearDirectoryNonRecursively() {
    listFiles()
        ?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
}

/**
 * @return The combined size in kb of all files in the directory, or 0 if it is not a directory
 */
fun File.getTotalSize(): Long =
    walkTopDown().sumOf { file ->
        if (file.isFile) file.length() else 0
    }

@Throws(IOException::class)
fun File.copyTo(destination: FileHandle) {
    inputStream().copyTo(destination)
}

@Throws(IOException::class)
fun InputStream.copyTo(destination: FileHandle) {
    use { input ->
        destination.write().use { output ->
            input.copyTo(output)
        }
    }
}
