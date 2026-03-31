package ch.threema.app.usecases

import ch.threema.app.test.testDispatcherProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ExportDebugLogUseCaseTest {

    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `create zip file from debug log files`() = runTest {
        val logFile1 = File.createTempFile("logfile", "1")
        val logFile2 = File.createTempFile("logfile", "2")
        logFile1.writeText("Hello\nWorld\n")
        logFile2.writeText("This is a test\n")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns listOf(logFile1, logFile2)
            },
            appDirectoryProvider = mockk {
                every { cacheDirectory } returns directory
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val exportedFile = useCase.call()

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
        assertEquals(
            "Hello\nWorld\nThis is a test\n",
            zipFile.getFileContents("debug_log.txt"),
        )
        assertEquals(
            "my meta data",
            zipFile.getFileContents("meta_data.txt"),
        )
    }

    @Test
    fun `create zip file from fallback debug log files`() = runTest {
        val logFile1 = File.createTempFile("logfile", "1")
        val logFile2 = File.createTempFile("logfile", "2")
        logFile1.writeText("Hello\nWorld\n")
        logFile2.writeText("This is a test\n")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns emptyList()
                every { getFallbackLogFiles() } returns listOf(logFile1, logFile2)
            },
            appDirectoryProvider = mockk {
                every { cacheDirectory } returns directory
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val exportedFile = useCase.call()

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
        assertEquals(
            "Hello\nWorld\nThis is a test\n",
            zipFile.getFileContents("debug_log.txt"),
        )
        assertEquals(
            "my meta data",
            zipFile.getFileContents("meta_data.txt"),
        )
    }

    @Test
    fun `previous file is deleted`() = runTest {
        val expectedFile = File(directory, "debug_log.zip")
        val logFile = File.createTempFile("logfile", "test")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns listOf(logFile)
            },
            appDirectoryProvider = mockk {
                every { cacheDirectory } returns directory
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        expectedFile.createNewFile()

        val exportedFile = useCase.call()
        assertEquals(expectedFile, exportedFile)

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
    }

    private fun ZipFile.getFileNames(): Set<String> =
        entries().toList().map { entry -> entry.name }.toSet()

    private fun ZipFile.getFileContents(fileName: String): String =
        getInputStream(getEntry(fileName)).use { inputStream ->
            inputStream.reader().readText()
        }
}
