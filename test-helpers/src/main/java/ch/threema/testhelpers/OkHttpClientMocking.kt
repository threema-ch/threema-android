package ch.threema.testhelpers

import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
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
): OkHttpClient {
    val builderMock = mockk<OkHttpClient.Builder>(relaxed = true)
    val clientMock = mockk<OkHttpClient> {
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
        every { newBuilder() } returns builderMock
    }
    every { builderMock.callTimeout(any<kotlin.time.Duration>()) } returns builderMock
    every { builderMock.callTimeout(any<java.time.Duration>()) } returns builderMock
    every { builderMock.build() } returns clientMock
    return clientMock
}

fun mockNoRequestOkHttpClient() =
    mockOkHttpClient { request ->
        fail("Expected no requests, but got $request")
    }

fun Request.buildResponse(block: Response.Builder.() -> Unit): Response =
    Response.Builder()
        .code(200)
        .protocol(Protocol.HTTP_2)
        .request(this)
        .message("")
        .apply(block)
        .build()

fun Request.respondWith(body: String = "", code: Int = 200) =
    buildResponse {
        code(code)
        message("")
        body(body.toResponseBody())
    }

fun Request.getBodyAsByteArray(): ByteArray? {
    val buffer = Buffer()
    (body ?: return null)
        .writeTo(buffer)
    return buffer.readByteArray()
}

fun Request.getBodyAsUtf8String(): String? =
    getBodyAsByteArray()?.toString(Charsets.UTF_8)

fun Request.getGZippedBodyAsUtf8String(): String? {
    val bytes = getBodyAsByteArray() ?: return null
    val outputStream = ByteArrayOutputStream()
    outputStream.use {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { inputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return String(outputStream.toByteArray())
}

fun Request.assertUrl(expected: String) {
    assertEquals(expected, url.toString())
}

fun Request.assertMethod(method: String) {
    assertEquals(method, this.method)
}

fun Request.assertHasHeader(name: String, value: String) {
    assertTrue(
        headers.any { header -> header.first.equals(name, ignoreCase = true) && header.second == value },
        "Expected header \"$name: $value\" not found.\n\n$headers",
    )
}

fun Request.assertGZippedBody(expected: String) {
    assertEquals(
        expected,
        getGZippedBodyAsUtf8String(),
    )
}
