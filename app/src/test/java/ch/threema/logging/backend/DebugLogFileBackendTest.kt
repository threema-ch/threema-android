package ch.threema.logging.backend

import android.util.Log
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.RuntimeException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DebugLogFileBackendTest {

    private lateinit var externalFilesDir: File
    private lateinit var internalFilesDir: File
    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var debugLogFileManagerMock: DebugLogFileManager
    private lateinit var backend: DebugLogFileBackend
    private lateinit var logFile: File
    private lateinit var fallbackLogFile: File

    @BeforeTest
    fun setUp() {
        externalFilesDir = createTempDirectory()
        internalFilesDir = createTempDirectory()
        testTimeProvider = TestTimeProvider(initialTimestamp = 1_763_000_000_000L)
        val logFileDirectory = File(externalFilesDir, "log")
        logFile = File(logFileDirectory, "debug_log.txt")
        fallbackLogFile = File(internalFilesDir, "debug_log.txt")
        debugLogFileManagerMock = mockk {
            every { getCurrentLogFile(any()) } returns logFile
            every { getCurrentFallbackLogFile(any()) } returns fallbackLogFile
            every { deleteLogFiles() } answers { logFile.delete() }
            every { deleteFallbackLogFiles() } answers { fallbackLogFile.delete() }
            every { createLogDirectory() } answers { logFileDirectory.mkdir() }
        }
        backend = DebugLogFileBackend(
            debugLogFileManager = debugLogFileManagerMock,
            minLogLevel = Log.INFO,
            handlerExecutor = mockk {
                every { post(any()) } answers {
                    firstArg<Runnable>().run()
                    true
                }
            },
            timeProvider = testTimeProvider,
        )
        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, false)
    }

    @AfterTest
    fun tearDown() {
        externalFilesDir.deleteRecursively()
        internalFilesDir.deleteRecursively()
    }

    @Test
    fun `backend is disabled by default, nothing is logged`() {
        backend.print(Log.INFO, TAG, throwable = null, message = "Hello")

        assertFalse(logFile.exists())
        assertFalse(fallbackLogFile.exists())
    }

    @Test
    fun `logs that meet min-level are written if backend is enabled`() {
        backend.print(Log.INFO, TAG, throwable = null, message = "Ignored")

        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, true)

        backend.print(Log.INFO, TAG, throwable = null, message = "Hello")
        testTimeProvider.advanceBy(10.seconds)

        backend.print(Log.DEBUG, TAG, throwable = null, message = "Terrible")
        testTimeProvider.advanceBy(10.minutes)

        backend.print(Log.ERROR, TAG, throwable = null, message = "World")
        testTimeProvider.advanceBy(10.hours)

        backend.print(Log.WARN, TAG, throwable = null, "How are {}?", args = arrayOf("you"))
        testTimeProvider.advanceBy(24.hours)

        val exception = RuntimeException("oh oh")
        backend.print(Log.ERROR, TAG, throwable = exception, "Oh look, an exception!")

        assertTrue(logFile.exists())
        assertFalse(fallbackLogFile.exists())
        assertEquals(
            """
            Thu Nov 13 03:13:20 CET 2025	INFO  DebugLogFileBackendTest: Hello
            Thu Nov 13 03:23:30 CET 2025	ERROR DebugLogFileBackendTest: World
            Thu Nov 13 13:23:30 CET 2025	WARN  DebugLogFileBackendTest: How are you?
            Fri Nov 14 13:23:30 CET 2025	ERROR DebugLogFileBackendTest: Oh look, an exception!
            """.trimIndent() + "\n" + exception.stackTraceToString().trim(),
            logFile.readText().trim(),
        )
    }

    @Test
    fun `fallback log file is written if default log file cannot be used`() {
        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, true)
        logFile.makeUnwritable()

        backend.print(Log.INFO, TAG, throwable = null, message = "Hello")
        testTimeProvider.advanceBy(10.seconds)
        backend.print(Log.ERROR, TAG, throwable = null, message = "World")

        assertTrue(fallbackLogFile.exists())
        assertEquals(
            """
            Thu Nov 13 03:13:20 CET 2025	INFO  DebugLogFileBackendTest: Hello
            Thu Nov 13 03:13:30 CET 2025	ERROR DebugLogFileBackendTest: World
            """.trimIndent(),
            fallbackLogFile.readText().trim(),
        )
    }

    @Test
    fun `fallback log file is deleted when default log file can be written`() {
        fallbackLogFile.createNewFile()

        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, true)
        backend.print(Log.INFO, TAG, throwable = null, message = "Hello")

        assertFalse(fallbackLogFile.exists())
    }

    @Test
    fun `log files are deleted when logging is disabled`() {
        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, true)
        logFile.createNewFile()
        fallbackLogFile.createNewFile()

        DebugLogFileBackend.setEnabled(debugLogFileManagerMock, false)

        assertFalse(logFile.exists())
        assertFalse(fallbackLogFile.exists())
    }

    companion object {
        private const val TAG = "DebugLogFileBackendTest"

        private fun File.makeUnwritable() {
            delete()
            mkdirs()
        }
    }
}
