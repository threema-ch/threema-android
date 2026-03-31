package ch.threema.app.errorreporting

import ch.threema.common.UUIDGenerator
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.loadResource
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.RuntimeException
import java.util.UUID.fromString as uuidFromString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorRecordStoreTest {

    private lateinit var recordsDirectory: File

    @BeforeTest
    fun setUp() {
        recordsDirectory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        recordsDirectory.deleteRecursively()
    }

    @Test
    fun `store exception`() {
        val timeProvider = TestTimeProvider(1_762_270_272_000L)
        val errorRecordStore = ErrorRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = timeProvider,
            uuidGenerator = uuidGenerator,
        )

        errorRecordStore.storeErrorForUnhandledException(createMockException())

        val recordFile = File(recordsDirectory, "p_${ERROR_RECORD_UUID}_v2.json")
        assertTrue(recordFile.exists())
        val content = recordFile.readText()
        assertEquals(
            loadResource("error-reporting/error-record-v2.json"),
            content,
        )
    }

    @Test
    fun `checking and deleting pending records`() {
        val errorRecordStore = ErrorRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
        )
        assertFalse(errorRecordStore.hasPendingRecords())

        errorRecordStore.storeErrorForUnhandledException(createMockException())
        assertTrue(errorRecordStore.hasPendingRecords())

        errorRecordStore.deletePendingRecords()
        assertFalse(errorRecordStore.hasPendingRecords())
    }

    @Test
    fun `confirm pending records`() {
        val timeProvider = TestTimeProvider(1_762_270_272_000L)
        val errorRecordStore = ErrorRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = timeProvider,
            uuidGenerator = uuidGenerator,
        )
        errorRecordStore.storeErrorForUnhandledException(createMockException())
        assertTrue(errorRecordStore.getConfirmedRecords().isEmpty())

        errorRecordStore.confirmPendingRecords()

        assertFalse(errorRecordStore.hasPendingRecords())

        val errorRecord = errorRecordStore.getConfirmedRecords().single()
        assertEquals(ERROR_RECORD_UUID, errorRecord.id.toString())
        assertEquals(timeProvider.get(), errorRecord.createdAt)
        assertEquals(
            ErrorRecord(
                id = uuidFromString(ERROR_RECORD_UUID),
                exceptions = listOf(
                    ErrorRecordExceptionDetails(
                        type = "IllegalStateException",
                        message = "This is the cause",
                        packageName = "java.lang",
                        stackTrace = listOf(
                            ErrorRecordStackTraceElement(
                                fileName = "Cause.kt",
                                className = "com.example.Cause",
                                lineNumber = 1337,
                                methodName = "causeTheCause",
                                isNative = false,
                            ),
                        ),
                    ),
                    ErrorRecordExceptionDetails(
                        type = "RuntimeException",
                        message = "This is a test",
                        packageName = "java.lang",
                        stackTrace = listOf(
                            ErrorRecordStackTraceElement(
                                fileName = "SomeOtherClass.kt",
                                className = "com.example.SomeOtherClass",
                                lineNumber = 42,
                                methodName = "fooBar",
                                isNative = true,
                            ),
                            ErrorRecordStackTraceElement(
                                fileName = "MyClass.kt",
                                className = "MyClass",
                                lineNumber = 67,
                                methodName = "testStuff",
                                isNative = false,
                            ),
                        ),
                    ),
                ),
                createdAt = timeProvider.get(),
            ),
            errorRecord,
        )
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        assertTrue(recordFile.exists())
    }

    @Test
    fun `deleting pending records does not delete confirmed records`() {
        val errorRecordStore = ErrorRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
        )
        errorRecordStore.storeErrorForUnhandledException(createMockException())
        errorRecordStore.confirmPendingRecords()
        errorRecordStore.storeErrorForUnhandledException(createMockException())

        errorRecordStore.deletePendingRecords()

        assertFalse(File(recordsDirectory, "p_${ERROR_RECORD_UUID}_v2.json").exists())
        assertTrue(File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json").exists())
    }

    @Test
    fun `delete confirmed record`() {
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        recordFile.createNewFile()
        val errorRecordStore = ErrorRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
        )

        errorRecordStore.deleteConfirmedRecord(uuidFromString(ERROR_RECORD_UUID))

        assertFalse(recordFile.exists())
    }

    companion object {
        private const val ERROR_RECORD_UUID = "d3bc6807-c4c7-47c2-af55-aa929d86fd09"

        private val uuidGenerator = UUIDGenerator {
            uuidFromString(ERROR_RECORD_UUID)
        }

        private fun createMockException() = mockk<RuntimeException> {
            every { message } returns "This is a test"
            every { stackTrace } returns arrayOf(
                mockk {
                    every { fileName } returns "MyClass.kt"
                    every { className } returns "MyClass"
                    every { lineNumber } returns 67
                    every { methodName } returns "testStuff"
                    every { isNativeMethod } returns false
                },
                mockk {
                    every { fileName } returns "SomeOtherClass.kt"
                    every { className } returns "com.example.SomeOtherClass"
                    every { lineNumber } returns 42
                    every { methodName } returns "fooBar"
                    every { isNativeMethod } returns true
                },
            )
            every { cause } returns mockk<IllegalStateException> {
                every { cause } returns null
                every { message } returns "This is the cause"
                every { stackTrace } returns arrayOf(
                    mockk {
                        every { fileName } returns "Cause.kt"
                        every { className } returns "com.example.Cause"
                        every { lineNumber } returns 1337
                        every { methodName } returns "causeTheCause"
                        every { isNativeMethod } returns false
                    },
                )
            }
        }
    }
}
