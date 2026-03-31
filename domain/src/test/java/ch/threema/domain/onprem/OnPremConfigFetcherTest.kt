package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.common.minus
import ch.threema.common.plus
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException
import org.json.JSONObject

class OnPremConfigFetcherTest {
    private lateinit var onPremConfigStoreMock: OnPremConfigStore

    @BeforeTest
    fun setUp() {
        onPremConfigStoreMock = mockk<OnPremConfigStore>(relaxed = true)
    }

    @Test
    fun `config is fetched and stored`() {
        val timeProvider = TestTimeProvider()
        val serverConfigParameters = serverParameters
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = timeProvider.get()) } returns mockOnPremConfig()
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverConfigParameters,
            timeProvider = timeProvider,
        )

        fetcher.getOrFetch()

        verify(exactly = 1) {
            okHttpClientMock.newCall(
                match { request ->
                    request.url == serverConfigParameters.oppfUrl.toHttpUrl() &&
                        request.header("Authorization") == BASIC_AUTH_HEADER_VALUE
                },
            )
        }
        verify(exactly = 1) {
            onPremConfigStoreMock.store(any())
        }
    }

    @Test
    fun `config is stored in in-memory cache and returned from it if cache is not stale`() {
        val timeProvider = TestTimeProvider()
        val config = mockOnPremConfig(validUntil = timeProvider.get() + 5.minutes)
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = any()) } returns config
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        fetcher.getOrFetch()

        timeProvider.advanceBy(5.minutes - 1.seconds)

        fetcher.getOrFetch()

        verify(exactly = 1) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `cached config is returned if it is valid`() {
        val timeProvider = TestTimeProvider()
        val config = mockOnPremConfig(validUntil = timeProvider.get() + 5.minutes)
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = any()) } returns config
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertNull(fetcher.getCached())

        fetcher.getOrFetch()

        assertNotNull(fetcher.getCached())

        timeProvider.advanceBy(5.minutes - 1.seconds)

        assertNotNull(fetcher.getCached())

        timeProvider.advanceBy(2.seconds)

        assertNull(fetcher.getCached())

        verify(exactly = 1) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `config is fetched again if in-memory cached config is stale`() {
        val timeProvider = TestTimeProvider()
        val config = mockOnPremConfig(validUntil = timeProvider.get() + 5.minutes)
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifier = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParser = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = any()) } returns config
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        fetcher.getOrFetch()

        timeProvider.advanceBy(5.minutes + 1.seconds)

        fetcher.getOrFetch()

        verify(exactly = 2) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `unexpected response is handled`() {
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(code = 500)
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = TestTimeProvider(),
        )

        assertFailsWith<ThreemaException> {
            fetcher.getOrFetch()
        }
    }

    @Test
    fun `retrying after auth error throws exception if it happens too soon`() {
        val timeProvider = TestTimeProvider()
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(code = 401)
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<UnauthorizedFetchException> {
            fetcher.getOrFetch()
        }

        timeProvider.advanceBy(3.minutes - 1.seconds)

        assertFailsWith<UnauthorizedFetchException> {
            fetcher.getOrFetch()
        }
    }

    @Test
    fun `retrying after auth error works after a delay`() {
        val timeProvider = TestTimeProvider()
        val responseMock = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns false
            every { code } returns 401
        }
        val okHttpClientMock = mockOkHttpClient {
            responseMock
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = any()) } returns mockOnPremConfig()
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<ThreemaException> {
            fetcher.getOrFetch()
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }
        timeProvider.advanceBy(3.minutes + 1.seconds)

        fetcher.getOrFetch()
    }

    @Test
    fun `retrying after a non-auth error works`() {
        val timeProvider = TestTimeProvider()
        val responseMock = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns false
            every { code } returns 400
        }
        val okHttpClientMock = mockOkHttpClient {
            responseMock
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = any()) } returns mockOnPremConfig()
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<ThreemaException> {
            fetcher.getOrFetch()
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }

        fetcher.getOrFetch()
    }

    @Test
    fun `io exception is handled`() {
        val okHttpClientMock = mockOkHttpClient {
            throw IOException()
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = TestTimeProvider(),
        )
        val exception = assertFailsWith<ThreemaException> {
            fetcher.getOrFetch()
        }
        assert(exception.cause is IOException)
    }

    @Test
    fun `expired license raises an exception`() {
        val timeProvider = TestTimeProvider()
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith("response-body")
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = okHttpClientMock,
            unpinnedOkHttpClient = mockk(),
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = timeProvider.get()) } returns mockOnPremConfig(licenseValidUntil = timeProvider.get() - 1.seconds)
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )

        val exception = assertFailsWith<ThreemaException> {
            fetcher.getOrFetch()
        }
        assertContains(exception.message!!, "license has expired")
    }

    @Test
    fun `config is fetched from fallback url, cached and stored`() {
        val timeProvider = TestTimeProvider()
        val serverConfigParameters = serverParameters
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = timeProvider.get()) } returns mockOnPremConfig()
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverConfigParameters,
            timeProvider = timeProvider,
        )

        assertNotNull(fetcher.fetchFallback())
        assertNotNull(fetcher.getCached())

        verify(exactly = 1) {
            okHttpClientMock.newCall(
                match { request ->
                    request.url == serverConfigParameters.oppfFallbackUrl.toHttpUrl() &&
                        request.header("Authorization") == null
                },
            )
        }
        verify(exactly = 1) {
            onPremConfigStoreMock.store(any())
        }
    }

    @Test
    fun `config is fetched from fallback url only once even when called multiple times in short succession`() {
        val timeProvider = TestTimeProvider()
        val serverConfigParameters = serverParameters
        val onPremConfigJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(responseBody)
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns onPremConfigJsonObjectMock
        }
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(onPremConfigJsonObjectMock, createdAt = any()) } returns mockOnPremConfig()
        }
        val fetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverConfigParameters,
            timeProvider = timeProvider,
        )

        fetcher.fetchFallback()
        timeProvider.advanceBy(1.seconds)
        fetcher.fetchFallback()
        timeProvider.advanceBy(1.seconds)
        fetcher.fetchFallback()

        verify(exactly = 1) {
            okHttpClientMock.newCall(any())
        }
        verify(exactly = 1) {
            onPremConfigStoreMock.store(any())
        }

        timeProvider.advanceBy(10.seconds)
        fetcher.fetchFallback()
        timeProvider.advanceBy(1.seconds)
        fetcher.fetchFallback()
        timeProvider.advanceBy(1.seconds)

        verify(exactly = 2) {
            okHttpClientMock.newCall(any())
        }
        verify(exactly = 2) {
            onPremConfigStoreMock.store(any())
        }
    }

    private fun mockOnPremConfig(
        validUntil: Instant = Instant.MAX,
        licenseValidUntil: Instant = Instant.MAX,
    ): OnPremConfig =
        mockk<OnPremConfig> {
            every { this@mockk.validUntil } returns validUntil
            every { license } returns mockk {
                every { expires } returns licenseValidUntil
            }
        }

    companion object {
        private val serverParameters = OnPremServerConfigParameters(
            oppfUrl = "https://example.com/config.oppf",
            username = "username",
            password = "password",
        )

        /**
         * Basic auth header for "username", using "password" as the password, as defined in [serverParameters].
         */
        private const val BASIC_AUTH_HEADER_VALUE = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
    }
}
