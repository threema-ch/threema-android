package ch.threema.logging.backend

import android.content.Context
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugLogFileManagerTest {

    private lateinit var appContextMock: Context
    private lateinit var externalDir: File
    private lateinit var logDir: File
    private lateinit var internalDir: File

    @BeforeTest
    fun setUp() {
        externalDir = createTempDirectory()
        logDir = File(externalDir, "log")
        internalDir = createTempDirectory()
        appContextMock = mockk {
            every { getExternalFilesDir(null) } returns externalDir
            every { filesDir } returns internalDir
        }
    }

    @AfterTest
    fun tearDown() {
        externalDir.deleteRecursively()
        internalDir.deleteRecursively()
    }

    @Test
    fun `get current log file`() {
        val now = Instant.ofEpochMilli(1_766_068_000_000L)
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        assertEquals(
            File(logDir, "debug_log_2025_12.txt"),
            debugLogFileManager.getCurrentLogFile(now),
        )
    }

    @Test
    fun `get current fallback log file`() {
        val now = Instant.ofEpochMilli(1_767_265_200_000L)
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        assertEquals(
            File(internalDir, "debug_log_2026_01.txt"),
            debugLogFileManager.getCurrentFallbackLogFile(now),
        )
    }

    @Test
    fun `empty list is returned when log directory does not exist`() {
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        assertEquals(
            emptyList(),
            debugLogFileManager.getLogFiles(),
        )
    }

    @Test
    fun `log files are returned in sorted order`() {
        logDir.mkdir()
        val logFile1 = File(logDir, "debug_log_1.txt")
        val logFile2 = File(logDir, "debug_log_2.txt")
        val logFile3 = File(logDir, "debug_log_3.txt")
        val logFiles = listOf(logFile1, logFile2, logFile3)
        logFiles.forEach { file -> file.createNewFile() }
        val unrelatedFile = File(logDir, "not_a_debug_log_at_all.txt")
        unrelatedFile.createNewFile()
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        assertEquals(
            listOf(logFile1, logFile2, logFile3),
            debugLogFileManager.getLogFiles(),
        )
    }

    @Test
    fun `fallback log files are returned in sorted order`() {
        val logFile1 = File(internalDir, "debug_log_1.txt")
        val logFile2 = File(internalDir, "debug_log_2.txt")
        val logFile3 = File(internalDir, "debug_log_3.txt")
        val logFiles = listOf(logFile1, logFile2, logFile3)
        logFiles.forEach { file -> file.createNewFile() }
        val unrelatedFile = File(internalDir, "not_a_debug_log_at_all.txt")
        unrelatedFile.createNewFile()
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        assertEquals(
            listOf(logFile1, logFile2, logFile3),
            debugLogFileManager.getFallbackLogFiles(),
        )
    }

    @Test
    fun `create log directory`() {
        val debugLogFileManager = DebugLogFileManager(appContextMock)
        assertFalse(logDir.exists())

        debugLogFileManager.createLogDirectory()

        assertTrue(logDir.exists())
    }

    @Test
    fun `delete log files`() {
        logDir.mkdir()
        val logFile1 = File(logDir, "debug_log_1.txt")
        val logFile2 = File(logDir, "debug_log_2.txt")
        val logFile3 = File(logDir, "debug_log_3.txt")
        val logFiles = listOf(logFile1, logFile2, logFile3)
        logFiles.forEach { file -> file.createNewFile() }
        val unrelatedFile = File(logDir, "not_a_debug_log_at_all.txt")
        unrelatedFile.createNewFile()
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        debugLogFileManager.deleteLogFiles()

        assertTrue(logDir.exists())
        assertTrue(unrelatedFile.exists())
        assertFalse(logFile1.exists())
        assertFalse(logFile2.exists())
        assertFalse(logFile3.exists())
    }

    @Test
    fun `delete fallback log files`() {
        val logFile1 = File(internalDir, "debug_log_1.txt")
        val logFile2 = File(internalDir, "debug_log_2.txt")
        val logFile3 = File(internalDir, "debug_log_3.txt")
        val logFiles = listOf(logFile1, logFile2, logFile3)
        logFiles.forEach { file -> file.createNewFile() }
        val unrelatedFile = File(internalDir, "not_a_debug_log_at_all.txt")
        unrelatedFile.createNewFile()
        val debugLogFileManager = DebugLogFileManager(appContextMock)

        debugLogFileManager.deleteFallbackLogFiles()

        assertTrue(unrelatedFile.exists())
        assertFalse(logFile1.exists())
        assertFalse(logFile2.exists())
        assertFalse(logFile3.exists())
    }
}
