package ch.threema.app.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.getThreemaLogger
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

private val logger = getThreemaLogger("StreamUtil")

@Throws(FileNotFoundException::class)
fun getFromUri(context: Context, uri: Uri?): InputStream? {
    var inputStream: InputStream? = null

    if (uri == null || uri.scheme == null) {
        throw FileNotFoundException()
    }

    if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
        try {
            inputStream = context.contentResolver.openInputStream(uri)
        } catch (_: FileNotFoundException) {
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
            val fileService = ThreemaApplication.requireServiceManager().fileService
            tmpPath = fileService.tempPath.absolutePath
            intTmpPath = fileService.intTmpPath.absolutePath
            appPath = context.applicationInfo.dataDir
        } catch (_: Exception) {
            return null
        }

        inputStream = if (filePath != null) {
            // do not allow sending of files from local directories - but allow tmp dir
            if (!filePath.startsWith(appPath) || filePath.startsWith(tmpPath) || filePath.startsWith(intTmpPath)) {
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
