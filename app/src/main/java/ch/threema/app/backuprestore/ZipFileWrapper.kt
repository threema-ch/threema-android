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

package ch.threema.app.backuprestore

import java.io.File
import java.io.IOException
import java.io.InputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.io.input.ProxyInputStream

/**
 * The [ZipFile] class has the problem that whenever [ZipFile.getInputStream] is called, it keeps a reference to the created input stream internally.
 * This is essentially a memory-leak, as these references aren't needed for anything other than being able to close all streams when calling
 * [ZipFile.close], which in our case is unnecessary as we already close them pro-actively. The [ZipFileWrapper] class comes in as a
 * helper to this problem, by ensuring that [ZipFile.close] is called pro-actively as well, making it remove the unnecessary internal reference.
 * The [ZipFile] instance can still be used normally afterwards.
 */
class ZipFileWrapper(
    file: File,
    password: String,
) {
    private val zipFile = ZipFile(file, password.toCharArray())

    fun isValidZipFile() =
        zipFile.isValidZipFile

    @Throws(ZipException::class)
    fun getFileHeaders(): List<FileHeader> =
        zipFile.fileHeaders

    fun getInputStream(fileHeader: FileHeader): InputStream =
        CloseCallbackInputStream(
            inputStream = zipFile.getInputStream(fileHeader),
            onClosed = {
                try {
                    zipFile.close()
                } catch (e: IOException) {
                    // should never happen, but if it does, we'd rather ignore it than cause a scene
                }
            },
        )

    private class CloseCallbackInputStream(
        inputStream: InputStream,
        private val onClosed: () -> Unit,
    ) : ProxyInputStream(inputStream) {
        override fun close() {
            super.close()
            onClosed()
        }
    }
}
