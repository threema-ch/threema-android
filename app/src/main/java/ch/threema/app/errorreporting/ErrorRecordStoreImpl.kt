package ch.threema.app.errorreporting

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.common.takeUnlessEmpty
import java.io.File
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

private val logger = getThreemaLogger("ErrorRecordStoreImpl")

@OptIn(ExperimentalSerializationApi::class)
class ErrorRecordStoreImpl
@VisibleForTesting
constructor(
    private val recordsDirectory: File,
    private val timeProvider: TimeProvider,
    private val uuidGenerator: UUIDGenerator,
    override val currentAppVersionCode: Int,
) : ErrorRecordStore {
    @WorkerThread
    override fun storeFatalError(e: Throwable) {
        storeError(
            level = ErrorRecordLevel.FATAL,
            e = e,
        )
    }

    @WorkerThread
    override fun storeHandledError(
        message: String,
        parameters: List<String>,
        e: Throwable?,
        attachments: List<File>?,
    ) {
        storeError(
            level = ErrorRecordLevel.ERROR,
            message = message,
            parameters = parameters,
            e = e,
            attachments = attachments,
        )
    }

    private fun storeError(
        level: ErrorRecordLevel,
        message: String? = null,
        parameters: List<String>? = null,
        e: Throwable? = null,
        attachments: List<File>? = null,
    ) {
        val id = uuidGenerator.generate()
        val errorRecord = ErrorRecord(
            id = id,
            level = level,
            exceptionDetails = e?.includingCauses()?.map { exception ->
                ErrorRecordExceptionDetails(
                    type = exception.getType(),
                    message = exception.message,
                    packageName = exception.javaClass.`package`?.name,
                    stackTrace = exception.getStackTraceElements(),
                )
            },
            message = message?.let {
                ErrorRecordMessage(
                    message = message.replace("{}", "%s"),
                    parameters = parameters,
                )
            },
            attachments = attachments?.map { attachmentFile ->
                ErrorRecordAttachment(
                    file = attachmentFile,
                    mimeType = attachmentFile.extension.takeUnlessEmpty()?.let { fileExtension ->
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
                    },
                )
            },
            createdAt = timeProvider.get(),
            versionCode = currentAppVersionCode,
        )

        recordsDirectory.mkdir()
        val recordFile = ErrorRecordFile.Pending.create(recordsDirectory, id, message)
        recordFile.write().use { outputStream ->
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
    override fun getErrorTypeIdsFromPendingRecords(): Set<ErrorTypeId?> =
        getPendingRecordFiles().map { it.errorTypeId }.toSet()

    override fun getPendingRecords(): Sequence<ErrorRecord> =
        readRecords(getPendingRecordFiles())

    @WorkerThread
    override fun deletePendingRecords() {
        getPendingRecordFiles().forEach { pendingRecordFile ->
            pendingRecordFile.delete()
        }
    }

    @WorkerThread
    override fun confirmPendingRecords() {
        getPendingRecordFiles().forEach { pendingRecordFile ->
            pendingRecordFile.confirm()
        }
    }

    @WorkerThread
    override fun getConfirmedRecords(): Sequence<ErrorRecord> =
        readRecords(getConfirmedRecordFiles())

    private fun readRecords(files: List<ErrorRecordFile>): Sequence<ErrorRecord> =
        files
            .asSequence()
            .mapNotNull { recordFile ->
                try {
                    recordFile.read().use { inputStream ->
                        Json.decodeFromStream<ErrorRecord>(inputStream)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to read error record, dropping", e)
                    recordFile.delete()
                    null
                }
            }

    @WorkerThread
    override fun deleteConfirmedRecord(id: UUID) {
        getConfirmedRecordFiles().forEach { recordFile ->
            if (recordFile.id == id) {
                recordFile.delete()
            }
        }
    }

    private fun getPendingRecordFiles(): List<ErrorRecordFile.Pending> =
        ErrorRecordFile.getPendingRecordFiles(recordsDirectory)

    private fun getConfirmedRecordFiles(): List<ErrorRecordFile.Confirmed> =
        ErrorRecordFile.getConfirmedRecordFiles(recordsDirectory)

    companion object {
        fun create(
            context: Context,
            timeProvider: TimeProvider,
            uuidGenerator: UUIDGenerator,
        ): ErrorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = getRecordsDirectory(context),
            timeProvider = timeProvider,
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = BuildConfig.DEFAULT_VERSION_CODE,
        )

        private fun getRecordsDirectory(context: Context): File =
            File(context.filesDir, "error-records")
    }
}
