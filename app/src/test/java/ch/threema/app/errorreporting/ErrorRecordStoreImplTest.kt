package ch.threema.app.errorreporting

import android.webkit.MimeTypeMap
import ch.threema.common.UUIDGenerator
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.loadResource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.lang.RuntimeException
import java.time.Instant
import java.util.UUID.fromString as uuidFromString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorRecordStoreImplTest {

    private lateinit var recordsDirectory: File

    @BeforeTest
    fun setUp() {
        recordsDirectory = createTempDirectory()
        mockkStatic(MimeTypeMap::getSingleton)
        every { MimeTypeMap.getSingleton() } returns mockk {
            every { getMimeTypeFromExtension("txt") } returns "text/plain"
        }
    }

    @AfterTest
    fun tearDown() {
        recordsDirectory.deleteRecursively()
        unmockkStatic(MimeTypeMap::getSingleton)
    }

    @Test
    fun `store fatal error with exception`() {
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(1_762_270_272_000L),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )

        errorRecordStore.storeFatalError(createMockException())

        val recordFile = File(recordsDirectory, "p_${ERROR_RECORD_UUID}_v2.json")
        assertTrue(recordFile.exists())
        assertEquals(
            loadResource("error-reporting/fatal-error-record-v2.json").trim(),
            recordFile.readText().trim(),
        )
    }

    @Test
    fun `store non-fatal error without exception`() {
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(1_762_270_272_000L),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )

        errorRecordStore.storeHandledError("This is a %s", listOf("test"), null)
        val expectedErrorTypeId = "8414d373fb7bc0aac3e2fc46647b8d0b7201c34c6d402c325f67a3d5c3dba8fe"
        assertEquals(setOf(ErrorTypeId(expectedErrorTypeId)), errorRecordStore.getErrorTypeIdsFromPendingRecords())

        val recordFile = File(recordsDirectory, "p_${ERROR_RECORD_UUID}__${expectedErrorTypeId}_v2.json")
        assertTrue(recordFile.exists())
        assertEquals(
            loadResource("error-reporting/non-fatal-error-record-v2.json").trim(),
            recordFile.readText().trim(),
        )
    }

    @Test
    fun `store non-fatal error with attachments`() {
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(1_762_270_272_000L),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )
        val file = File("/some/file.txt")

        errorRecordStore.storeHandledError("here be attachments", attachments = listOf(file))

        val recordFile = recordsDirectory.listFiles()?.first()!!
        assertTrue(recordFile.exists())
        assertEquals(
            loadResource("error-reporting/non-fatal-error-record-with-attachment-v2.json").trim(),
            recordFile.readText().trim(),
        )
    }

    @Test
    fun `checking and deleting pending records`() {
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )
        assertEquals(emptySet(), errorRecordStore.getErrorTypeIdsFromPendingRecords())

        errorRecordStore.storeFatalError(createMockException())
        assertEquals(setOf(null), errorRecordStore.getErrorTypeIdsFromPendingRecords())

        errorRecordStore.deletePendingRecords()
        assertEquals(emptySet(), errorRecordStore.getErrorTypeIdsFromPendingRecords())
    }

    @Test
    fun `confirm pending records`() {
        val timeProvider = TestTimeProvider(1_762_270_272_000L)
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = timeProvider,
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )
        errorRecordStore.storeFatalError(createMockException())
        assertTrue(errorRecordStore.getConfirmedRecords().toList().isEmpty())

        errorRecordStore.confirmPendingRecords()

        assertEquals(emptySet(), errorRecordStore.getErrorTypeIdsFromPendingRecords())

        val errorRecord = errorRecordStore.getConfirmedRecords().single()
        assertEquals(ERROR_RECORD_UUID, errorRecord.id.toString())
        assertEquals(timeProvider.get(), errorRecord.createdAt)
        assertEquals(
            ErrorRecord(
                id = uuidFromString(ERROR_RECORD_UUID),
                level = ErrorRecordLevel.FATAL,
                exceptionDetails = listOf(
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
                versionCode = 1234,
            ),
            errorRecord,
        )
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        assertTrue(recordFile.exists())
    }

    @Test
    fun `restore fatal error record`() {
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        recordFile.writeText(loadResource("error-reporting/fatal-error-record-v2.json"))

        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )

        val errorRecord = errorRecordStore.getConfirmedRecords().single()
        assertEquals(
            ErrorRecord(
                id = uuidFromString(ERROR_RECORD_UUID),
                level = ErrorRecordLevel.FATAL,
                exceptionDetails = listOf(
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
                createdAt = Instant.ofEpochMilli(1762270272000),
                versionCode = 1234,
            ),
            errorRecord,
        )
    }

    @Test
    fun `restore non-fatal error record`() {
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        recordFile.writeText(loadResource("error-reporting/non-fatal-error-record-v2.json"))

        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )

        val errorRecord = errorRecordStore.getConfirmedRecords().single()
        assertEquals(
            ErrorRecord(
                id = uuidFromString(ERROR_RECORD_UUID),
                level = ErrorRecordLevel.ERROR,
                message = ErrorRecordMessage(
                    message = "This is a %s",
                    parameters = listOf("test"),
                ),
                createdAt = Instant.ofEpochMilli(1762270272000),
                versionCode = 1234,
            ),
            errorRecord,
        )
    }

    @Test
    fun `deleting pending records does not delete confirmed records`() {
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )
        errorRecordStore.storeFatalError(createMockException())
        errorRecordStore.confirmPendingRecords()
        errorRecordStore.storeFatalError(createMockException())

        errorRecordStore.deletePendingRecords()

        assertFalse(File(recordsDirectory, "p_${ERROR_RECORD_UUID}_v2.json").exists())
        assertTrue(File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json").exists())
    }

    @Test
    fun `delete confirmed record`() {
        val recordFile = File(recordsDirectory, "c_${ERROR_RECORD_UUID}_v2.json")
        recordFile.createNewFile()
        val recordFile2 = File(recordsDirectory, "c_${ERROR_RECORD_UUID2}_v2.json")
        recordFile2.createNewFile()
        val errorRecordStore = ErrorRecordStoreImpl(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = uuidGenerator,
            currentAppVersionCode = 1234,
        )

        errorRecordStore.deleteConfirmedRecord(uuidFromString(ERROR_RECORD_UUID))

        assertFalse(recordFile.exists())
        assertTrue(recordFile2.exists())
    }

    companion object {
        private const val ERROR_RECORD_UUID = "d3bc6807-c4c7-47c2-af55-aa929d86fd09"
        private const val ERROR_RECORD_UUID2 = "d3bc6807-c4c7-47c2-af55-aa929d86fd0a"

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
