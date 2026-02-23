package ch.threema.app.webclient.services

import ch.threema.app.webclient.services.WebSessionQRCodeParser.InvalidQrCodeException
import ch.threema.base.utils.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSessionQRCodeParserImplTest {

    private val parser = WebSessionQRCodeParserImpl()

    @Test
    @Throws(Exception::class)
    fun parseServerKey() {
        // arrange
        val base64QrCodeContent =
            "BTkCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkIjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjI" +
                "yMjIxM3EzcTNxM3EzcTNxM3EzcTNxM3EzcTNxM3EzcTNxM3BNJzYWx0eXJ0Yy5leGFtcGxlLm9yZw=="
        val payloadBytes = Base64.decode(base64QrCodeContent)

        // act
        val result = parser.parse(payloadBytes)

        // assert
        assertNotNull(result)
        assertEquals("saltyrtc.example.org", result.saltyRtcHost)
        assertEquals(1234, result.saltyRtcPort)
        assertTrue(result.isPermanent)
        assertContentEquals(
            byteArrayOf(
                35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
                35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            ),
            result.authToken,
        )
        assertContentEquals(
            byteArrayOf(
                66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
                66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
            ),
            result.key,
        )
        assertContentEquals(
            byteArrayOf(
                19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55,
                19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55,
            ),
            result.serverKey,
        )
    }

    @Test
    @Throws(Exception::class)
    fun parseNoneServerKey() {
        // arrange
        val base64QrCodeContent =
            "BTkCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkIjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyM" +
                "jIyMjIwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABNJzYWx0eXJ0Yy5leGFtcGxlLm9yZw=="
        val payloadBytes = Base64.decode(base64QrCodeContent)

        // act
        val result = parser.parse(payloadBytes)

        // assert
        assertNotNull(result)
        assertEquals("saltyrtc.example.org", result.saltyRtcHost)
        assertEquals(1234, result.saltyRtcPort)
        assertTrue(result.isPermanent)
        assertContentEquals(
            byteArrayOf(
                35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
                35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            ),
            result.authToken,
        )
        assertContentEquals(
            byteArrayOf(
                66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
                66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
            ),
            result.key,
        )

        // no server key
        assertNull(result.serverKey)
    }

    @Test
    fun shouldThrowExceptionWhenInputTooShort() {
        // arrange
        val invalidInput = "ocF9rNLyW6P4whzuFC36xg=="
        val payloadBytes = Base64.decode(invalidInput)

        // act & assert
        assertFailsWith<InvalidQrCodeException> {
            parser.parse(payloadBytes)
        }
    }
}
