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

package ch.threema.common.files

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

class SimpleFileHandle(
    private val file: File,
) : FileHandle {
    constructor(directory: File, name: String) : this(File(directory, name))

    override fun exists() = file.exists()

    override fun isEmpty() = file.exists() && file.length() == 0L

    @Throws(IOException::class)
    override fun read(): FileInputStream? =
        try {
            file.inputStream()
        } catch (_: FileNotFoundException) {
            null
        }

    @Throws(IOException::class)
    override fun create() {
        file.parentFile?.mkdirs()
        file.createNewFile()
    }

    @Throws(IOException::class)
    override fun write(): OutputStream {
        file.parentFile?.mkdirs()
        return file.outputStream()
    }

    @Throws(IOException::class)
    override fun delete() {
        if (!file.exists()) {
            return
        }
        if (!file.delete()) {
            throw IOException("Failed to delete file")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SimpleFileHandle
        return file == other.file
    }

    override fun hashCode() = file.hashCode()

    override fun toString() = "File[${file.absolutePath}]"
}
