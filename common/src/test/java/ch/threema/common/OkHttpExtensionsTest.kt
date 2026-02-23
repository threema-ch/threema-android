package ch.threema.common

import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.Protocol
import okhttp3.Response

class OkHttpExtensionsTest {
    @Test
    fun `get expiration date from header`() {
        val response = buildResponse {
            header("Expires", "Mon, 22 Sep 2025 13:37:00 GMT")
        }

        assertEquals(
            Instant.parse("2025-09-22T13:37:00Z"),
            response.getExpiration(),
        )
    }

    @Test
    fun `expiration date is null when no header is available`() {
        val response = buildResponse()

        assertNull(response.getExpiration())
    }

    @Test
    fun `expiration date is null when no header contains invalid value`() {
        val response = buildResponse {
            header("Expires", "Mon, 32 Sep 2025 13:37:00 GMT")
        }

        assertNull(response.getExpiration())
    }

    private fun buildResponse(block: Response.Builder.() -> Unit = {}) =
        Response.Builder()
            .protocol(Protocol.HTTP_2)
            .request(mockk())
            .code(200)
            .message("")
            .apply(block)
            .build()
}
