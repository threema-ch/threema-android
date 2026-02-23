package ch.threema.base.utils

import ch.threema.common.toCryptographicByteArray
import com.google.protobuf.ByteString
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ProtobufExtensionsTest {
    @Test
    fun `cryptographic array to byte string`() {
        assertEquals(
            ByteString.copyFrom(byteArrayOf(1, 2, 3)),
            byteArrayOf(1, 2, 3).toCryptographicByteArray().toByteString(),
        )
    }

    @Test
    fun `byte string to cryptographic array`() {
        assertEquals(
            byteArrayOf(1, 2, 3).toCryptographicByteArray(),
            ByteString.copyFrom(byteArrayOf(1, 2, 3)).toCryptographicByteArray(),
        )
    }

    @Test
    fun `generate random padding`() {
        val secureRandomMock = mockk<SecureRandom> {
            every { nextInt(256) } returns 10
        }

        assertContentEquals(
            byteArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
            generateRandomProtobufPadding(secureRandomMock).toByteArray(),
        )
    }
}
