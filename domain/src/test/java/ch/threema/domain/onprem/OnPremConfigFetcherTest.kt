/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException
import org.json.JSONObject

class OnPremConfigFetcherTest {
    private val onPremConfigStoreMock = mockk<OnPremConfigStore>(relaxed = true)

    @Test
    fun `config is fetched`() {
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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverConfigParameters,
            timeProvider = timeProvider,
        )

        fetcher.fetch()

        verify(exactly = 1) {
            okHttpClientMock.newCall(
                match { request ->
                    request.url == serverConfigParameters.url.toHttpUrl() &&
                        request.header("Authorization") == BASIC_AUTH_HEADER_VALUE
                },
            )
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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        fetcher.fetch()

        timeProvider.advanceBy(5.minutes - 1.seconds)

        fetcher.fetch()

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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        fetcher.fetch()

        timeProvider.advanceBy(5.minutes + 1.seconds)

        fetcher.fetch()

        verify(exactly = 2) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `unexpected response is handled`() {
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(code = 500)
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = TestTimeProvider(),
        )

        assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }
    }

    @Test
    fun `retrying after auth error throws exception if it happens too soon`() {
        val timeProvider = TestTimeProvider()
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(code = 401)
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }

        timeProvider.advanceBy(3.minutes - 1.seconds)

        assertFailsWith<UnauthorizedFetchException> {
            fetcher.fetch()
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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = any()) } returns mockOnPremConfig()
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }
        timeProvider.advanceBy(3.minutes + 1.seconds)

        fetcher.fetch()
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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = any()) } returns mockOnPremConfig()
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )
        assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }

        fetcher.fetch()
    }

    @Test
    fun `io exception is handled`() {
        val okHttpClientMock = mockOkHttpClient {
            throw IOException()
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = TestTimeProvider(),
        )
        val exception = assertFailsWith<ThreemaException> {
            fetcher.fetch()
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
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any(), createdAt = timeProvider.get()) } returns mockOnPremConfig(licenseValidUntil = timeProvider.get() - 1.seconds)
            },
            onPremConfigStore = onPremConfigStoreMock,
            serverParameters = serverParameters,
            timeProvider = timeProvider,
        )

        val exception = assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }
        assertContains(exception.message!!, "license has expired")
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
            url = "https://example.com/",
            username = "username",
            password = "password",
        )

        /**
         * Basic auth header for "username", using "password" as the password, as defined in [serverParameters].
         */
        private const val BASIC_AUTH_HEADER_VALUE = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
    }
}
