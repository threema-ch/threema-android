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

package ch.threema.common

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.coroutines.executeAsync

fun buildRequest(block: Request.Builder.() -> Unit): Request =
    Request.Builder().apply(block).build()

fun OkHttpClient.buildNew(block: OkHttpClient.Builder.() -> Unit): OkHttpClient =
    newBuilder().apply(block).build()

fun OkHttpClient.Builder.withUserAgent(userAgent: String) =
    addInterceptor { chain ->
        chain.proceed(
            chain.request()
                .newBuilder()
                .header(Http.Header.USER_AGENT, userAgent)
                .build(),
        )
    }

@Throws(IOException::class)
fun OkHttpClient.execute(request: Request): Response =
    newCall(request).execute()

@Throws(IOException::class)
suspend inline fun OkHttpClient.executeAsync(request: Request): Response =
    newCall(request).executeAsync()

@Throws(HttpResponseException::class)
fun Response.getSuccessBodyOrThrow(): ResponseBody =
    (if (isSuccessful) body else null)
        ?: throw HttpResponseException(code)

@Throws(HttpResponseException::class)
fun Response.throwIfNotSuccessful() {
    if (!isSuccessful) {
        throw HttpResponseException(code)
    }
}

class HttpResponseException(val code: Int) : IOException("Request was unsuccessful, status code was $code")

fun Response.getExpiration(): Instant? =
    header(Http.Header.EXPIRES)
        ?.let {
            try {
                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }

@Throws(IOException::class)
fun ResponseBody.copyIntoFile(file: File) {
    byteStream().use { inputStream ->
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
            outputStream.flush()
        }
    }
}
