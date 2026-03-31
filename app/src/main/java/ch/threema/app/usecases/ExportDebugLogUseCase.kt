package ch.threema.app.usecases

import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.FileHandlingZipOutputStream
import ch.threema.app.utils.FileHandlingZipOutputStream.Companion.initializeZipOutputStream
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toEnumeration
import ch.threema.logging.backend.DebugLogFileManager
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import kotlinx.coroutines.withContext
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod

private val logger = getThreemaLogger("ExportDebugLogUseCase")

class ExportDebugLogUseCase(
    private val getDebugMetaDataUseCase: GetDebugMetaDataUseCase,
    private val debugLogFileManager: DebugLogFileManager,
    private val appDirectoryProvider: AppDirectoryProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    @Throws(IOException::class, SecurityException::class)
    suspend fun call(): File = withContext(dispatcherProvider.io) {
        val zipFile = File(appDirectoryProvider.cacheDirectory, ZIP_FILE_NAME)
        if (zipFile.exists() && !zipFile.delete()) {
            logger.error("Failed to delete zip file")
        }
        createZipFile(zipFile)
        zipFile
    }

    private fun createZipFile(zipFile: File) {
        initializeZipOutputStream(zipFile, null).use { zipOutputStream ->
            val logFiles = debugLogFileManager.getLogFiles()
                .ifEmpty { debugLogFileManager.getFallbackLogFiles() }
            zipOutputStream.writeLogFile(logFiles)
            zipOutputStream.writeMetaDataFile()
        }
    }

    /**
     * Concatenates the contents of all [files] and writes them as a single file into the zip file.
     */
    private fun FileHandlingZipOutputStream.writeLogFile(files: List<File>) {
        SequenceInputStream(files.map { FileInputStream(it) }.toEnumeration()).use { inputStream ->
            addFile(DEBUG_LOG_FILE_NAME, inputStream)
        }
    }

    private fun FileHandlingZipOutputStream.writeMetaDataFile() {
        val metaData = try {
            getDebugMetaDataUseCase.call()
        } catch (e: Exception) {
            logger.error("Failed to compile meta data", e)
            return
        }
        addFile(META_DATA_FILE_NAME, metaData.byteInputStream())
    }

    private fun FileHandlingZipOutputStream.addFile(fileName: String, contents: InputStream) {
        val parameters = createZipParameters(fileName)
        putNextEntry(parameters)
        contents.copyTo(this)
        closeEntry()
    }

    private fun createZipParameters(filenameInZip: String?): ZipParameters =
        ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
            this.fileNameInZip = filenameInZip
        }

    companion object {
        private const val ZIP_FILE_NAME = "debug_log.zip"
        private const val DEBUG_LOG_FILE_NAME = "debug_log.txt"
        private const val META_DATA_FILE_NAME = "meta_data.txt"
    }
}
