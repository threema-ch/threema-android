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

package ch.threema.localcrypto

import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.Base64
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.WorkClientInfo
import ch.threema.libthreema.HttpsMethod
import ch.threema.libthreema.HttpsRequest
import ch.threema.libthreema.HttpsResponse
import ch.threema.libthreema.HttpsResult
import ch.threema.localcrypto.MasterKeyTestData.AUTH_TOKEN
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET_HASH
import ch.threema.localcrypto.MasterKeyTestData.WORK_URL
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretParameters
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RemoteSecretClientTest {
    @Test
    fun `monitor remote secret`() = runTest {
        val httpClientMock = mockk<LibthreemaHttpClient> {
            coEvery { sendHttpsRequest(any()) } answers {
                val request = firstArg<HttpsRequest>()
                val body = request.body.toString(Charsets.UTF_8)
                if (request.url == "${WORK_URL}api-client/v1/remote-secret" && request.method == HttpsMethod.POST) {
                    assertTrue(Base64.encodeBytes(AUTH_TOKEN.value) in body)
                    HttpsResult.Response(
                        v1 = HttpsResponse(
                            status = 200.toUShort(),
                            body = """
                                {
                                    "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                                    "checkIntervalS": 11,
                                    "nMissedChecksMax": 1
                                }
                        """.toByteArray(),
                        ),
                    )
                } else {
                    fail("Unexpected request: ${request.method} ${request.url}")
                }
            }
        }
        val client = RemoteSecretClient(
            clientInfo = CLIENT_INFO,
            httpClientWithOnPremCertPinning = httpClientMock,
            httpClientWithoutOnPremCertPinning = httpClientMock,
        )
        val monitor = client.createRemoteSecretLoop(
            baseUrl = WORK_URL,
            parameters = RemoteSecretParameters(
                authenticationToken = AUTH_TOKEN,
                remoteSecretHash = REMOTE_SECRET_HASH,
            ),
        )

        val job = launch {
            monitor.run()
        }

        assertEquals(REMOTE_SECRET, monitor.remoteSecret.await())
        job.cancel()
    }

    @Test
    fun `create remote secret`() = runTest {
        val httpClientMock = mockk<LibthreemaHttpClient> {
            coEvery { sendHttpsRequest(any()) } answers {
                val request = firstArg<HttpsRequest>()
                val body = request.body.toString(Charsets.UTF_8)
                if (request.url == "${WORK_URL}api-client/v1/remote-secret" && request.method == HttpsMethod.PUT) {
                    assertTrue(USERNAME in body)
                    assertTrue(PASSWORD in body)
                    assertTrue(IDENTITY in body)
                    HttpsResult.Response(
                        v1 = HttpsResponse(
                            status = 200.toUShort(),
                            body = if ("challenge" in body) {
                                """{
                                       "secretAuthenticationToken": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
                                   }"""
                            } else {
                                """{
                                       "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                                       "challenge": "bWVvdw=="
                                   }"""
                            }
                                .toByteArray(),
                        ),
                    )
                } else {
                    fail("Unexpected request: ${request.method} ${request.url}")
                }
            }
        }
        val client = RemoteSecretClient(
            clientInfo = CLIENT_INFO,
            httpClientWithOnPremCertPinning = httpClientMock,
            httpClientWithoutOnPremCertPinning = mockk(),
        )

        val result = client.createRemoteSecret(
            parameters = CLIENT_PARAMETERS,
        )

        assertEquals(AUTH_TOKEN, result.parameters.authenticationToken)
    }

    @Test
    fun `creating remote secret fails with invalid credentials`() = runTest {
        val httpClientMock = mockk<LibthreemaHttpClient> {
            coEvery { sendHttpsRequest(any()) } answers {
                HttpsResult.Response(
                    v1 = HttpsResponse(
                        status = 401.toUShort(),
                        body = """{ "code": "invalid-credentials" }""".toByteArray(),
                    ),
                )
            }
        }
        val client = RemoteSecretClient(
            clientInfo = CLIENT_INFO,
            httpClientWithOnPremCertPinning = httpClientMock,
            httpClientWithoutOnPremCertPinning = mockk(),
        )

        assertFailsWith<InvalidCredentialsException> {
            client.createRemoteSecret(
                parameters = CLIENT_PARAMETERS,
            )
        }
    }

    @Test
    fun `delete remote secret`() = runTest {
        val httpClientMock = mockk<LibthreemaHttpClient> {
            coEvery { sendHttpsRequest(any()) } answers {
                val request = firstArg<HttpsRequest>()
                val body = request.body.toString(Charsets.UTF_8)
                if (request.url == "${WORK_URL}api-client/v1/remote-secret" && request.method == HttpsMethod.DELETE) {
                    assertTrue(USERNAME in body)
                    assertTrue(PASSWORD in body)
                    assertTrue(IDENTITY in body)
                    HttpsResult.Response(
                        v1 = HttpsResponse(
                            status = 200.toUShort(),
                            body = """
                                {
                                    "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                                    "challenge": "bWVvdw=="
                                }
                        """.toByteArray(),
                        ),
                    )
                } else {
                    fail("Unexpected request: ${request.method} ${request.url}")
                }
            }
        }
        val client = RemoteSecretClient(
            clientInfo = CLIENT_INFO,
            httpClientWithOnPremCertPinning = httpClientMock,
            httpClientWithoutOnPremCertPinning = mockk(),
        )

        client.deleteRemoteSecret(
            parameters = CLIENT_PARAMETERS,
            authenticationToken = AUTH_TOKEN,
        )
    }

    companion object {
        private val CLIENT_INFO = WorkClientInfo(
            appVersion = "1.2.3",
            appLocale = "de_CH",
            deviceModel = "Test",
            osVersion = "999",
            workFlavor = WorkClientInfo.WorkFlavor.ON_PREM,
        )
        private const val IDENTITY = "01234567"
        private const val USERNAME = "Testy McTestface"
        private const val PASSWORD = "Password1"
        private val CLIENT_PARAMETERS = RemoteSecretClientParameters(
            workServerBaseUrl = WORK_URL,
            userIdentity = IDENTITY,
            clientKey = ByteArray(NaCl.SECRET_KEY_BYTES) { it.toByte() }.toCryptographicByteArray(),
            credentials = UserCredentials(
                username = USERNAME,
                password = PASSWORD,
            ),
        )
    }
}
