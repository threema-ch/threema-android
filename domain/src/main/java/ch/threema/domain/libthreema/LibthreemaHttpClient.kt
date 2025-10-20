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

package ch.threema.domain.libthreema

import ch.threema.common.buildNew
import ch.threema.common.buildRequest
import ch.threema.common.executeAsync
import ch.threema.libthreema.HttpsException
import ch.threema.libthreema.HttpsMethod
import ch.threema.libthreema.HttpsRequest
import ch.threema.libthreema.HttpsResponse
import ch.threema.libthreema.HttpsResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse

class LibthreemaHttpClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun sendHttpsRequest(request: HttpsRequest): HttpsResult {
        val okHttpResponse = try {
            okHttpClient.buildNew { callTimeout(request.timeout) }
                .executeAsync(request.toOkHttpRequest())
        } catch (e: IllegalArgumentException) {
            return HttpsResult.Error(HttpsException.InvalidRequest(e.message.orEmpty()))
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            return HttpsResult.Error(
                when (e) {
                    is InterruptedIOException -> HttpsException.Timeout(message)
                    is UnknownHostException,
                    is SocketException,
                    -> HttpsException.Unreachable(message)
                    else -> HttpsException.Unclassified(message)
                },
            )
        }
        return try {
            okHttpResponse.use {
                it.toHttpsResponse().let(HttpsResult::Response)
            }
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            HttpsResult.Error(
                when (e) {
                    is InterruptedIOException -> HttpsException.Timeout(message)
                    else -> HttpsException.InvalidResponse(message)
                },
            )
        }
    }

    private fun HttpsRequest.toOkHttpRequest(): OkHttpRequest =
        buildRequest {
            url(url)
            headers.forEach { (name, value) ->
                header(name, value)
            }
            when (method) {
                HttpsMethod.GET -> get()
                HttpsMethod.POST -> post(body.toRequestBody())
                HttpsMethod.PUT -> put(body.toRequestBody())
                HttpsMethod.DELETE -> delete(body.toRequestBody())
            }
        }

    @Throws(IOException::class)
    private fun OkHttpResponse.toHttpsResponse(): HttpsResponse =
        HttpsResponse(
            status = code.toUShort(),
            body = body.bytes(),
        )
}
