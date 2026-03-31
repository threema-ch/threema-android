package ch.threema.app.errorreporting

import ch.threema.common.minus
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.assertGZippedBody
import ch.threema.testhelpers.assertHasHeader
import ch.threema.testhelpers.assertMethod
import ch.threema.testhelpers.assertUrl
import ch.threema.testhelpers.loadResource
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class SentryServiceTest {

    private lateinit var okHttpClientMock: OkHttpClient
    private lateinit var handleRequest: (Request) -> Response
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var sentryService: SentryService

    @BeforeTest
    fun setUp() {
        okHttpClientMock = mockOkHttpClient { request ->
            handleRequest(request)
        }
        timeProvider = TestTimeProvider(initialTimestamp = 1_769_423_000_000L)
        sentryService = SentryService(
            okHttpClient = okHttpClientMock,
            timeProvider = timeProvider,
            sentryIdProvider = mockk {
                every { getOrGenerateSentryId() } returns SENTRY_ID
            },
            config = SentryService.Config(
                host = HOST,
                projectId = PROJECT_ID,
                publicApiKey = PUBLIC_API_KEY,
            ),
            metaInfo = SentryService.MetaInfo(
                androidSdkVersion = 35,
                appVersion = "6.4.0",
                versionCode = 1234,
                buildFlavor = "test",
                deviceModel = "Awesome Phone XY",
            ),
        )
    }

    @Test
    fun `send error record`() = runTest {
        handleRequest = { request ->
            request.assertMethod("POST")
            request.assertUrl("https://$HOST/api/$PROJECT_ID/envelope/")
            request.assertHasHeader(
                "X-Sentry-Auth",
                "Sentry sentry_version=7, sentry_client=threema.android/1.0, sentry_key=$PUBLIC_API_KEY",
            )
            request.assertHasHeader(
                "User-Agent",
                "threema.android/1.0",
            )
            request.assertHasHeader(
                "Content-Type",
                "application/x-sentry-envelope",
            )
            request.assertGZippedBody(loadResource("api/requests/sentry-envelope.txt"))

            request.respondWith(code = 200)
        }

        sentryService.sendErrorRecord(
            ErrorRecord(
                id = UUID.fromString(ERROR_ID),
                exceptions = listOf(
                    ErrorRecordExceptionDetails(
                        type = "RuntimeException",
                        message = "An error occurred",
                        packageName = "ch.threema",
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
                                className = "ch.threema.MyClass",
                                lineNumber = 67,
                                methodName = "testStuff",
                                isNative = false,
                            ),
                        ),
                    ),
                    ErrorRecordExceptionDetails(
                        type = "IllegalStateException",
                        message = "Another exception",
                        packageName = "com.example",
                        stackTrace = listOf(
                            ErrorRecordStackTraceElement(
                                fileName = "TestClass.kt",
                                className = "com.example.TestClass",
                                lineNumber = 1,
                                methodName = "testing",
                                isNative = false,
                            ),
                        ),
                    ),
                ),
                createdAt = timeProvider.get() - 10.minutes,
            ),
        )

        verify(exactly = 1) { okHttpClientMock.newCall(any()) }
    }

    companion object {
        private const val HOST = "test.threema.ch"
        private const val PROJECT_ID = 42
        private const val PUBLIC_API_KEY = "abcd1234"
        private const val SENTRY_ID = "98385cec-e748-468c-9c5e-a30ff6a458d0"
        private const val ERROR_ID = "d6d528df-f4d0-4e7d-81a5-3c2404486c39"
    }
}
