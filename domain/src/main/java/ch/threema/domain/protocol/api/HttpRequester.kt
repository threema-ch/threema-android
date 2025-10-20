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

package ch.threema.domain.protocol.api

import ch.threema.common.Http
import ch.threema.common.buildNew
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.getUserAgent
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class HttpRequester(
    okHttpClient: OkHttpClient,
    private val authenticator: APIAuthenticator?,
    private val language: String? = null,
    private val version: Version,
) {
    private val okHttpClient = okHttpClient.buildNew {
        connectTimeout(ProtocolDefines.API_REQUEST_TIMEOUT.seconds)
        readTimeout(ProtocolDefines.API_REQUEST_TIMEOUT.seconds)
    }

    @Throws(IOException::class)
    fun get(url: String): HttpRequesterResult =
        sendRequest(url) {
            get()
        }

    @Throws(IOException::class)
    fun post(url: String, body: JSONObject): HttpRequesterResult =
        sendRequest(url) {
            post(body.toString().toRequestBody("application/json".toMediaType()))
        }

    @Throws(IOException::class)
    private fun sendRequest(url: String, buildRequest: Request.Builder.() -> Unit): HttpRequesterResult {
        val request = buildRequest {
            url(url)
            header(Http.Header.USER_AGENT, getUserAgent(version))
            if (language != null) {
                header(Http.Header.ACCEPT_LANGUAGE, language)
            }
            authenticator?.getCredentials()?.let { credentials ->
                header(Http.Header.AUTHORIZATION, Credentials.basic(credentials.username, credentials.password))
            }
            buildRequest()
        }

        okHttpClient.execute(request).use { response ->
            if (response.isSuccessful) {
                return HttpRequesterResult.Success(response.getSuccessBodyOrThrow().string())
            }
            return HttpRequesterResult.Error(response.code)
        }
    }
}
