package ch.threema.app.errorreporting

import android.content.Context
import androidx.annotation.WorkerThread
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import java.io.File
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
class ErrorRecordStore(
    private val recordsDirectory: File,
    private val timeProvider: TimeProvider,
    private val uuidGenerator: UUIDGenerator,
) {
    @WorkerThread
    fun storeErrorForUnhandledException(e: Throwable) {
        val id = uuidGenerator.generate()
        val errorRecord = ErrorRecord(
            id = id,
            exceptions = e.includingCauses().map { exception ->
                ErrorRecordExceptionDetails(
                    type = exception.getType(),
                    message = exception.message,
                    packageName = exception.javaClass.`package`?.name,
                    stackTrace = exception.getStackTraceElements(),
                )
            },
            createdAt = timeProvider.get(),
        )

        recordsDirectory.mkdir()
        val recordFile = File(recordsDirectory, PENDING_PREFIX + id + FILE_SUFFIX)

        recordFile.outputStream().use { outputStream ->
            Json.encodeToStream(errorRecord, outputStream)
        }
    }

    private fun Throwable.includingCauses(): List<Throwable> {
        val exceptions = mutableListOf<Throwable>()
        val circularityDetector = mutableSetOf<Throwable>()
        var currentThrowable: Throwable = this
        while (circularityDetector.add(currentThrowable)) {
            exceptions.add(0, currentThrowable)
            currentThrowable = currentThrowable.cause ?: break
        }
        return exceptions
    }

    private fun Throwable.getType(): String {
        javaClass.`package`?.name?.let { packageName ->
            return javaClass.name.replace("$packageName.", "")
        }
        return javaClass.name
    }

    private fun Throwable.getStackTraceElements() =
        stackTrace.reversed().map { element ->
            ErrorRecordStackTraceElement(
                fileName = element.fileName,
                className = element.className,
                lineNumber = element.lineNumber,
                methodName = element.methodName,
                isNative = element.isNativeMethod,
            )
        }

    @WorkerThread
    fun hasPendingRecords(): Boolean =
        getPendingRecordFiles().isNotEmpty()

    @WorkerThread
    fun deletePendingRecords() {
        getPendingRecordFiles().forEach { pendingRecordFile ->
            pendingRecordFile.delete()
        }
    }

    @WorkerThread
    fun confirmPendingRecords() {
        getPendingRecordFiles().forEach { pendingRecordFile ->
            val confirmedRecordFile = File(recordsDirectory, CONFIRMED_PREFIX + pendingRecordFile.name.removePrefix(PENDING_PREFIX))
            pendingRecordFile.renameTo(confirmedRecordFile)
        }
    }

    @WorkerThread
    fun getConfirmedRecords(): List<ErrorRecord> =
        getRecordFiles(prefix = CONFIRMED_PREFIX)
            .map { confirmedRecordFile ->
                confirmedRecordFile.inputStream().use { inputStream ->
                    Json.decodeFromStream<ErrorRecord>(inputStream)
                }
            }

    @WorkerThread
    fun deleteConfirmedRecord(id: UUID) {
        File(recordsDirectory, CONFIRMED_PREFIX + id + FILE_SUFFIX).delete()
    }

    private fun getPendingRecordFiles(): Array<File> =
        getRecordFiles(prefix = PENDING_PREFIX)

    private fun getRecordFiles(prefix: String): Array<File> =
        recordsDirectory.listFiles { file ->
            file.name.startsWith(prefix) && file.name.endsWith(FILE_SUFFIX)
        }
            ?: emptyArray()

    companion object {
        fun getRecordsDirectory(context: Context): File =
            File(context.filesDir, "error-records")

        private const val PENDING_PREFIX = "p_"
        private const val CONFIRMED_PREFIX = "c_"
        private const val FILE_SUFFIX = "_v2.json"
    }
}
