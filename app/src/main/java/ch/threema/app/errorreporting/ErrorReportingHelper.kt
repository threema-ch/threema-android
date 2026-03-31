package ch.threema.app.errorreporting

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.DispatcherProvider
import kotlinx.coroutines.withContext

class ErrorReportingHelper(
    private val preferenceService: PreferenceService,
    private val errorRecordStore: ErrorRecordStore,
    private val sendErrorReportWorkerScheduler: SendErrorReportWorker.Scheduler,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun processPendingErrorRecords(): CheckResult = withContext(dispatcherProvider.io) {
        if (!hasPendingRecords()) {
            return@withContext CheckResult.DO_NOTHING
        }
        return@withContext when (preferenceService.getErrorReportingState()) {
            PreferenceService.ErrorReportingState.ALWAYS_ASK -> CheckResult.SHOW_DIALOG
            PreferenceService.ErrorReportingState.ALWAYS_SEND -> {
                confirmRecordsAndScheduleSending()
                CheckResult.DO_NOTHING
            }
            PreferenceService.ErrorReportingState.NEVER_SEND -> {
                deletePendingRecords()
                CheckResult.DO_NOTHING
            }
        }
    }

    private suspend fun hasPendingRecords() = withContext(dispatcherProvider.io) {
        errorRecordStore.hasPendingRecords()
    }

    suspend fun deletePendingRecords() = withContext(dispatcherProvider.io) {
        errorRecordStore.deletePendingRecords()
    }

    suspend fun confirmRecordsAndScheduleSending() = withContext(dispatcherProvider.io) {
        errorRecordStore.confirmPendingRecords()
        sendErrorReportWorkerScheduler.schedule()
    }

    enum class CheckResult {
        DO_NOTHING,
        SHOW_DIALOG,
    }
}
