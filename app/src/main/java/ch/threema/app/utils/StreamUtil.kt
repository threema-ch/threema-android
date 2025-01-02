/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.LoggingUtil
import org.slf4j.Logger
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

private val logger: Logger = LoggingUtil.getThreemaLogger("StreamUtil")

@Throws(FileNotFoundException::class)
fun getFromUri(context: Context, uri: Uri?): InputStream? {
    var inputStream: InputStream? = null

    if (uri == null || uri.scheme == null) {
        throw FileNotFoundException()
    }

    if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
        try {
            inputStream = context.contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            logger.info("Unable to get an InputStream for this file using ContentResolver: $uri")
        }
    }

    if (inputStream == null) {
        // try to open as local file if openInputStream fails for a content Uri
        val filePath = FileUtil.getRealPathFromURI(context, uri)
        val appPath: String
        val tmpPath: String
        val intTmpPath: String

        try {
            tmpPath =
                ThreemaApplication.getServiceManager()!!.fileService.tempPath.absolutePath
            intTmpPath =
                ThreemaApplication.getServiceManager()!!.fileService.intTmpPath.absolutePath
            appPath = context.applicationInfo.dataDir
        } catch (e: Exception) {
            return null
        }

        inputStream = if (TestUtil.required(filePath, appPath, tmpPath)) {
            // do not allow sending of files from local directories - but allow tmp dir
            if (!filePath!!.startsWith(appPath) || filePath.startsWith(tmpPath) || filePath.startsWith(
                    intTmpPath
                )
            ) {
                FileInputStream(filePath)
            } else {
                throw FileNotFoundException("File on private directory")
            }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }
    return inputStream
}

fun InputStream?.contentEquals(byteArray: ByteArray?): Boolean {
    if (this == null && byteArray == null) {
        return true
    }

    if (this == null) {
        return false
    }

    if (byteArray == null) {
        return false
    }

    use { input ->
        var index = 0
        var next: Int
        while (input.read().also { next = it } != -1) {
            if (next.toByte() != byteArray[index]) {
                return false
            }
            index++
        }
        return index == byteArray.size
    }
}

fun InputStream?.toByteArray(): ByteArray? {
    if (this == null) {
        return null
    }

    return buffered().use(InputStream::readBytes)
}
