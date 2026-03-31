package ch.threema.app.logging

import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.logging.backend.DebugLogFileManager
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.test.ClosingKoinTest
import org.koin.test.mock.declare

class DebugLogFileCleanupWorkerTest : ClosingKoinTest {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @Test
    fun `log files older than 60 days are deleted, newer ones are kept`() = runTest {
        val oldLogFile1 = mockFile(age = 63.days)
        val oldLogFile2 = mockFile(age = 62.days)
        val oldLogFile3 = mockFile(age = 61.days)
        val oldFallbackLogFile1 = mockFile(age = 63.days)
        val oldFallbackLogFile2 = mockFile(age = 62.days)
        val oldFallbackLogFile3 = mockFile(age = 61.days)
        val newLogFile1 = mockFile(age = 60.days)
        val newLogFile2 = mockFile(age = 59.days)
        val newFallbackLogFile1 = mockFile(age = 60.days)
        val newFallbackLogFile2 = mockFile(age = 59.days)

        val debugLogFileManagerMock = mockk<DebugLogFileManager> {
            every { getLogFiles() } returns listOf(
                oldLogFile1,
                oldLogFile2,
                oldLogFile3,
                newLogFile1,
                newLogFile2,
            )
            every { getFallbackLogFiles() } returns listOf(
                oldFallbackLogFile1,
                oldFallbackLogFile2,
                oldFallbackLogFile3,
                newFallbackLogFile1,
                newFallbackLogFile2,
            )
        }
        val testTimeProvider = TestTimeProvider(now)
        declare { debugLogFileManagerMock }
        declare<TimeProvider> { testTimeProvider }
        val worker = DebugLogFileCleanupWorker(mockk(), mockk())

        worker.doWork()

        verify(exactly = 1) { oldLogFile1.delete() }
        verify(exactly = 1) { oldLogFile2.delete() }
        verify(exactly = 1) { oldLogFile3.delete() }
        verify(exactly = 1) { oldFallbackLogFile1.delete() }
        verify(exactly = 1) { oldFallbackLogFile2.delete() }
        verify(exactly = 1) { oldFallbackLogFile3.delete() }
        verify(exactly = 0) { newLogFile1.delete() }
        verify(exactly = 0) { newLogFile2.delete() }
        verify(exactly = 0) { newFallbackLogFile1.delete() }
        verify(exactly = 0) { newFallbackLogFile2.delete() }
    }

    private fun mockFile(age: Duration): File {
        val fileName = "${age.inWholeDays}d"
        return mockk(fileName) {
            every { name } returns fileName
            every { lastModified() } returns (now - age).toEpochMilli()
            every { delete() } returns true
        }
    }

    companion object {
        private val now = Instant.ofEpochMilli(1_766_068_000_000L)
    }
}
