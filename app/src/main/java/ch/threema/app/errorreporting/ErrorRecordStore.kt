package ch.threema.app.errorreporting

import androidx.annotation.WorkerThread
import ch.threema.logging.BaseErrorRecordStore
import java.util.UUID

interface ErrorRecordStore : BaseErrorRecordStore {
    /**
     * Returns all [ErrorTypeId]s from all pending records. May also include `null` in case there are one or more error records without
     * an [ErrorTypeId], as is the case for fatal errors.
     */
    @WorkerThread
    fun getErrorTypeIdsFromPendingRecords(): Set<ErrorTypeId?>

    @WorkerThread
    fun getPendingRecords(): Sequence<ErrorRecord>

    @WorkerThread
    fun deletePendingRecords()

    @WorkerThread
    fun confirmPendingRecords()

    @WorkerThread
    fun getConfirmedRecords(): Sequence<ErrorRecord>

    @WorkerThread
    fun deleteConfirmedRecord(id: UUID)

    val currentAppVersionCode: Int
}
