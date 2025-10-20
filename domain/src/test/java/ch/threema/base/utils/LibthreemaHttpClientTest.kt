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

package ch.threema.base.utils

import ch.threema.common.emptyByteArray
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.libthreema.HttpsException
import ch.threema.libthreema.HttpsHeader
import ch.threema.libthreema.HttpsMethod
import ch.threema.libthreema.HttpsRequest
import ch.threema.libthreema.HttpsResult
import ch.threema.testhelpers.getBodyAsByteArray
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import java.net.NoRouteToHostException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.test.runTest

class LibthreemaHttpClientTest {
    @Test
    fun `successful get request with headers`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                assertEquals(URL, request.url.toString())
                assertEquals("Test1", request.header("Header1"))
                assertEquals("Test2", request.header("Header2"))
                assertNull(request.body)
                request.respondWith(RESPONSE_BODY)
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.GET,
                headers = listOf(
                    HttpsHeader("Header1", "Test1"),
                    HttpsHeader("Header2", "Test2"),
                ),
                body = emptyByteArray(),
            ),
        )

        assertTrue(result is HttpsResult.Response)
        assertEquals(200.toUShort(), result.v1.status)
        assertContentEquals(RESPONSE_BODY.toByteArray(), result.v1.body)
    }

    @Test
    fun `successful post request`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("POST", request.method)
                assertEquals(URL, request.url.toString())
                assertContentEquals(REQUEST_BODY, request.getBodyAsByteArray())
                request.respondWith(RESPONSE_BODY)
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.POST,
                headers = emptyList(),
                body = REQUEST_BODY,
            ),
        )

        assertTrue(result is HttpsResult.Response)
        assertEquals(200.toUShort(), result.v1.status)
        assertContentEquals(RESPONSE_BODY.toByteArray(), result.v1.body)
    }

    @Test
    fun `successful put request`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("PUT", request.method)
                assertEquals(URL, request.url.toString())
                assertContentEquals(REQUEST_BODY, request.getBodyAsByteArray())
                request.respondWith(RESPONSE_BODY)
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.PUT,
                headers = emptyList(),
                body = REQUEST_BODY,
            ),
        )

        assertTrue(result is HttpsResult.Response)
        assertEquals(200.toUShort(), result.v1.status)
        assertContentEquals(RESPONSE_BODY.toByteArray(), result.v1.body)
    }

    @Test
    fun `successful delete request`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("DELETE", request.method)
                assertEquals(URL, request.url.toString())
                assertContentEquals(REQUEST_BODY, request.getBodyAsByteArray())
                request.respondWith(RESPONSE_BODY)
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.DELETE,
                headers = emptyList(),
                body = REQUEST_BODY,
            ),
        )

        assertTrue(result is HttpsResult.Response)
        assertEquals(200.toUShort(), result.v1.status)
        assertContentEquals(RESPONSE_BODY.toByteArray(), result.v1.body)
    }

    @Test
    fun `get request fails with 4xx response`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient { request ->
                request.respondWith(RESPONSE_BODY, code = 404)
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.GET,
                headers = emptyList(),
                body = emptyByteArray(),
            ),
        )

        assertTrue(result is HttpsResult.Response)
        assertEquals(404.toUShort(), result.v1.status)
        assertContentEquals(RESPONSE_BODY.toByteArray(), result.v1.body)
    }

    @Test
    fun `get request fails with network error`() = runTest {
        val httpClient = LibthreemaHttpClient(
            okHttpClient = mockOkHttpClient {
                throw NoRouteToHostException("network issue")
            },
        )

        val result = httpClient.sendHttpsRequest(
            HttpsRequest(
                timeout = 1000.milliseconds.toJavaDuration(),
                url = URL,
                method = HttpsMethod.GET,
                headers = emptyList(),
                body = emptyByteArray(),
            ),
        )

        assertTrue(result is HttpsResult.Error)
        assertTrue(result.v1 is HttpsException.Unreachable)
        assertEquals("v1=network issue", result.v1.message)
    }

    companion object {
        private const val URL = "https://test/"
        private val REQUEST_BODY = byteArrayOf(1, 2, 3, 4)
        private const val RESPONSE_BODY = "response"
    }
}
