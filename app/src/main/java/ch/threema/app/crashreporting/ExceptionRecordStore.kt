package ch.threema.app.crashreporting

import android.content.Context
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.common.clearDirectoryRecursively
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
class ExceptionRecordStore(
    private val recordsDirectory: File,
    private val timeProvider: TimeProvider,
    private val uuidGenerator: UUIDGenerator,
) {
    fun storeException(e: Throwable) {
        val id = uuidGenerator.generate()
        val exceptionRecord = ExceptionRecord(
            id = id,
            stackTrace = e.stackTraceToString(),
            createdAt = timeProvider.get(),
        )

        recordsDirectory.mkdir()
        val recordFile = File(recordsDirectory, id + FILE_SUFFIX)

        recordFile.outputStream().use { outputStream ->
            Json.encodeToStream(exceptionRecord, outputStream)
        }
    }

    fun hasRecords(): Boolean =
        recordsDirectory.listFiles { file ->
            file.name.endsWith(FILE_SUFFIX)
        }
            ?.isNotEmpty() == true

    fun deleteRecords() {
        recordsDirectory.clearDirectoryRecursively()
    }

    companion object {
        fun getRecordsDirectory(context: Context): File =
            File(context.filesDir, "exceptions")

        private const val FILE_SUFFIX = "_v1.json"
    }
}
