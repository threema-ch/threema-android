package ch.threema.app.errorreporting

import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ErrorReportDetailsProviderTest {

    @Test
    fun `generate error report details`() = runTest {
        val errorReportDetailsProvider = ErrorReportDetailsProvider(
            errorRecordStore = mockk {
                every { getPendingRecords() } returns sequenceOf(
                    fatalErrorRecord,
                    nonFatalErrorRecord,
                    nonFatalErrorRecord.copy(
                        message = nonFatalErrorRecord.message?.copy(
                            parameters = listOf("foo"),
                        ),
                    ),
                )
            },
            sentryServiceMetaInfo = SentryService.MetaInfo(
                deviceModel = "Testing",
                androidSdkVersion = 35,
                release = "9.9.9",
                versionCode = 9,
                buildFlavor = "TEST",
            ),
            dispatcherProvider = testDispatcherProvider(),
        )

        assertEquals(
            """
                MetaInfo(deviceModel=Testing, androidSdkVersion=35, release=9.9.9, versionCode=9, buildFlavor=TEST)

                Crash
                java.langRuntimeException: This is a test
                  - MyClass.testStuff@67
                  - com.example.SomeOtherClass.fooBar@42
                java.langIllegalStateException: This is the cause
                  - com.example.Cause.causeTheCause@1337

                Error
                This is a test
            """.trimIndent(),
            errorReportDetailsProvider.get(),
        )
    }

    companion object {
        private const val ERROR_RECORD_UUID = "d3bc6807-c4c7-47c2-af55-aa929d86fd09"
        private const val ERROR_RECORD_UUID2 = "d3bc6807-c4c7-47c2-af55-aa929d86fd0a"

        private val fatalErrorRecord = ErrorRecord(
            id = UUID.fromString(ERROR_RECORD_UUID),
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
        )
        private val nonFatalErrorRecord = ErrorRecord(
            id = UUID.fromString(ERROR_RECORD_UUID2),
            level = ErrorRecordLevel.ERROR,
            message = ErrorRecordMessage(
                message = "This is a %s",
                parameters = listOf("test"),
            ),
            createdAt = Instant.ofEpochMilli(1762270272000),
            versionCode = 1234,
        )
    }
}
