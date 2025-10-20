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

package ch.threema.app.threemasafe

import ch.threema.common.HttpResponseException
import ch.threema.testhelpers.buildResponse
import ch.threema.testhelpers.getBodyAsByteArray
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.ResponseBody.Companion.toResponseBody

class ThreemaSafeApiClientTest {
    @Test
    fun `test server`() {
        val apiClient = ThreemaSafeApiClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                assertEquals("https://example.com/config", request.url.toString())
                assertEquals(AUTHORIZATION, request.header("Authorization"))

                request.respondWith("""{"maxBackupBytes":2097152,"retentionDays":180}""")
            },
        )

        val response = apiClient.testServer(serverInfoMock, backupId)

        assertEquals(
            ThreemaSafeServerTestResponse(
                maxBackupBytes = 2_097_152,
                retentionDays = 180,
            ),
            response,
        )
    }

    @Test
    fun `download backup`() {
        val apiClient = ThreemaSafeApiClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                assertEquals("https://example.com/backup", request.url.toString())
                assertEquals(AUTHORIZATION, request.header("Authorization"))

                request.buildResponse {
                    body(byteArrayOf(8, 7, 6, 5).toResponseBody())
                }
            },
        )

        val data = apiClient.downloadEncryptedBackup(serverInfoMock, backupId)

        assertContentEquals(
            byteArrayOf(8, 7, 6, 5),
            data,
        )
    }

    @Test
    fun `download backup fails with error response`() {
        val apiClient = ThreemaSafeApiClient(
            okHttpClient = mockOkHttpClient { request ->
                request.respondWith(code = 404)
            },
        )

        val e = assertFailsWith<HttpResponseException> {
            apiClient.downloadEncryptedBackup(serverInfoMock, backupId)
        }
        assertEquals(404, e.code)
    }

    @Test
    fun `upload backup`() {
        val apiClient = ThreemaSafeApiClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("PUT", request.method)
                assertEquals("https://example.com/backup", request.url.toString())
                assertEquals(AUTHORIZATION, request.header("Authorization"))
                assertContentEquals(byteArrayOf(1, 3, 5, 7), request.getBodyAsByteArray())

                request.respondWith(code = 200)
            },
        )

        apiClient.uploadEncryptedBackup(serverInfoMock, backupId, backupData = byteArrayOf(1, 3, 5, 7))
    }

    @Test
    fun `delete backup`() {
        val apiClient = ThreemaSafeApiClient(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("DELETE", request.method)
                assertEquals("https://example.com/backup", request.url.toString())
                assertEquals(AUTHORIZATION, request.header("Authorization"))

                request.respondWith(code = 200)
            },
        )

        apiClient.deleteBackup(serverInfoMock, backupId)
    }

    companion object {
        private val backupId: ThreemaSafeBackupId = byteArrayOf(1, 2, 3, 4)
        private const val AUTHORIZATION = "Token 123"

        val serverInfoMock = mockk<ThreemaSafeServerInfo> {
            every { getConfigUrl(backupId) } returns URL("https://example.com/config")
            every { getBackupUrl(backupId) } returns URL("https://example.com/backup")
            every { authorization } returns AUTHORIZATION
        }
    }
}
