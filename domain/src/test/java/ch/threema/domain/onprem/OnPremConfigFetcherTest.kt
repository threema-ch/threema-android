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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException
import org.json.JSONObject

class OnPremConfigFetcherTest {
    @Test
    fun `config is fetched`() {
        val timeProvider = TestTimeProvider()
        val serverConfigParameters = serverParameters
        val oppfJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val responseBodyMock = mockk<ResponseBody> {
            every { string() } returns responseBody
        }
        val responseMock = mockk<Response> {
            every { isSuccessful } returns true
            every { body } returns responseBodyMock
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val onPremConfigVerifier = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns oppfJsonObjectMock
        }
        val onPremConfigParser = mockk<OnPremConfigParser> {
            every { parse(oppfJsonObjectMock) } returns mockOnPremConfig()
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
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
    fun `config is returned from cache if cache is not stale`() {
        val testTimeProvider = TestTimeProvider()
        val config = mockOnPremConfig(cacheValidUntil = testTimeProvider.get() + 5.minutes)
        val oppfJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val responseBodyMock = mockk<ResponseBody> {
            every { string() } returns responseBody
        }
        val responseMock = mockk<Response> {
            every { isSuccessful } returns true
            every { body } returns responseBodyMock
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val onPremConfigVerifier = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns oppfJsonObjectMock
        }
        val onPremConfigParser = mockk<OnPremConfigParser> {
            every { parse(oppfJsonObjectMock) } returns config
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )
        fetcher.fetch()

        testTimeProvider.advanceBy(5.minutes - 1.seconds)

        fetcher.fetch()

        verify(exactly = 1) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `config is fetched again if cached config is stale`() {
        val testTimeProvider = TestTimeProvider()
        val config = mockOnPremConfig(cacheValidUntil = testTimeProvider.get() + 5.minutes)
        val oppfJsonObjectMock = mockk<JSONObject>()
        val responseBody = "response-body"
        val responseBodyMock = mockk<ResponseBody> {
            every { string() } returns responseBody
        }
        val responseMock = mockk<Response> {
            every { isSuccessful } returns true
            every { body } returns responseBodyMock
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val onPremConfigVerifier = mockk<OnPremConfigVerifier> {
            every { verify(responseBody) } returns oppfJsonObjectMock
        }
        val onPremConfigParser = mockk<OnPremConfigParser> {
            every { parse(oppfJsonObjectMock) } returns config
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )
        fetcher.fetch()

        testTimeProvider.advanceBy(5.minutes + 1.seconds)

        fetcher.fetch()

        verify(exactly = 2) { okHttpClientMock.newCall(any()) }
    }

    @Test
    fun `unexpected response is handled`() {
        val responseMock = mockk<Response> {
            every { isSuccessful } returns false
            every { code } returns 500
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            serverParameters = serverParameters,
            timeProvider = TestTimeProvider(),
        )

        assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }
    }

    @Test
    fun `retrying after auth error throws exception if it happens too soon`() {
        val testTimeProvider = TestTimeProvider()
        val responseMock = mockk<Response> {
            every { isSuccessful } returns false
            every { code } returns 401
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )
        try {
            fetcher.fetch()
        } catch (e: ThreemaException) {
            // ignore exception, as it is expected
        }

        testTimeProvider.advanceBy(3.minutes - 1.seconds)

        assertFailsWith<UnauthorizedFetchException> {
            fetcher.fetch()
        }
    }

    @Test
    fun `retrying after auth error works after a delay`() {
        val testTimeProvider = TestTimeProvider()
        val responseMock = mockk<Response> {
            every { isSuccessful } returns false
            every { code } returns 401
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any()) } returns mockOnPremConfig()
            },
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )
        try {
            fetcher.fetch()
        } catch (e: ThreemaException) {
            // ignore exception, as it is expected
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }
        testTimeProvider.advanceBy(3.minutes + 1.seconds)

        fetcher.fetch()
    }

    @Test
    fun `retrying after a non-auth error works`() {
        val testTimeProvider = TestTimeProvider()
        val responseMock = mockk<Response> {
            every { isSuccessful } returns false
            every { code } returns 400
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any()) } returns mockOnPremConfig()
            },
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )
        try {
            fetcher.fetch()
        } catch (e: ThreemaException) {
            // ignore exception, as it is expected
        }

        every { responseMock.isSuccessful } returns true
        every { responseMock.body } returns mockk<ResponseBody> {
            every { string() } returns "mock-response"
        }

        fetcher.fetch()
    }

    @Test
    fun `io exception is handled`() {
        val callMock = mockk<Call> {
            every { execute() } throws IOException()
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(),
            onPremConfigParser = mockk(),
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
        val testTimeProvider = TestTimeProvider()
        val responseBodyMock = mockk<ResponseBody> {
            every { string() } returns "response-body"
        }
        val responseMock = mockk<Response> {
            every { isSuccessful } returns true
            every { body } returns responseBodyMock
        }
        val callMock = mockk<Call> {
            every { execute() } returns responseMock
        }
        val okHttpClientMock = mockk<OkHttpClient> {
            every { newCall(any()) } returns callMock
        }
        val fetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClientMock,
            onPremConfigVerifier = mockk(relaxed = true),
            onPremConfigParser = mockk {
                every { parse(any()) } returns mockOnPremConfig(licenseValidUntil = testTimeProvider.get() - 1.seconds)
            },
            serverParameters = serverParameters,
            timeProvider = testTimeProvider,
        )

        val exception = assertFailsWith<ThreemaException> {
            fetcher.fetch()
        }
        assertContains(exception.message!!, "license has expired")
    }

    private fun mockOnPremConfig(
        cacheValidUntil: Instant = Instant.MAX,
        licenseValidUntil: Instant = Instant.MAX,
    ): OnPremConfig =
        mockk<OnPremConfig> {
            every { validUntil } returns cacheValidUntil
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
