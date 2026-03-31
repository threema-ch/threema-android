package ch.threema.app.errorreporting

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.test.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ErrorReportingHelperTest {

    @Test
    fun `no pending records`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.NEVER_SEND
            },
            errorRecordStore = mockk {
                every { hasPendingRecords() } returns false
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
    }

    @Test
    fun `always ask`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_ASK
            },
            errorRecordStore = mockk {
                every { hasPendingRecords() } returns true
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.SHOW_DIALOG, result)
    }

    @Test
    fun `always send`() = runTest {
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { hasPendingRecords() } returns true
            every { confirmPendingRecords() } just runs
        }
        val sendErrorReportWorkerScheduler = mockk<SendErrorReportWorker.Scheduler> {
            every { schedule() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_SEND
            },
            errorRecordStore = errorRecordStoreMock,
            sendErrorReportWorkerScheduler = sendErrorReportWorkerScheduler,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
        verify(exactly = 1) { errorRecordStoreMock.confirmPendingRecords() }
        verify(exactly = 1) { sendErrorReportWorkerScheduler.schedule() }
    }

    @Test
    fun `never send`() = runTest {
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { hasPendingRecords() } returns true
            every { deletePendingRecords() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.NEVER_SEND
            },
            errorRecordStore = errorRecordStoreMock,
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
        verify(exactly = 1) { errorRecordStoreMock.deletePendingRecords() }
    }
}
