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

package ch.threema.testhelpers

import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

fun mockOkHttpClient(
    createResponse: (Request) -> Response,
): OkHttpClient =
    mockk<OkHttpClient> {
        every { newCall(any()) } answers {
            val request = firstArg<Request>()
            mockk<Call> {
                val call = this
                every { execute() } answers {
                    createResponse(request)
                }
                every { enqueue(any()) } answers {
                    val callback = firstArg<Callback>()
                    try {
                        val response = createResponse(request)
                        callback.onResponse(call, response)
                    } catch (e: IOException) {
                        callback.onFailure(call, e)
                    }
                }
            }
        }
    }

fun Request.buildResponse(block: Response.Builder.() -> Unit): Response =
    Response.Builder()
        .protocol(Protocol.HTTP_2)
        .request(this)
        .apply(block)
        .build()

fun Request.respondWith(body: String = "", code: Int = 200) =
    buildResponse {
        code(code)
        message("")
        body(body.toResponseBody())
    }

fun Request.getBodyAsUtf8String(): String? {
    val buffer = Buffer()
    (body ?: return null)
        .writeTo(buffer)
    return buffer.readUtf8()
}
