package ch.threema.common

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputStreamExtensionsTest {
    @Test
    fun testEqual() {
        val bytes = byteArrayOf(0, 1, 2, 3)
        val inputStream = ByteArrayInputStream(bytes)
        assertTrue(inputStream.contentEquals(bytes))
    }

    @Test
    fun testDifferentLength() {
        val bytes = byteArrayOf(0, 1, 2, 3)

        // Assert false when the provided byte array is longer
        assertFalse(ByteArrayInputStream(bytes).contentEquals(bytes + 11))

        // Assert false when the provided byte array is shorter
        assertFalse(ByteArrayInputStream(bytes).contentEquals(bytes.copyOf(bytes.size - 2)))
    }

    @Test
    fun testDifferentContent() {
        val inputBytes = byteArrayOf(0, 1, 2, 3)

        assertFalse(ByteArrayInputStream(inputBytes).contentEquals(byteArrayOf(0, 1, 2, 42)))
        assertFalse(ByteArrayInputStream(inputBytes).contentEquals(byteArrayOf(42, 1, 2, 3)))
        assertFalse(ByteArrayInputStream(inputBytes).contentEquals(byteArrayOf(42, 42, 42, 42)))
        assertFalse(ByteArrayInputStream(inputBytes).contentEquals(byteArrayOf(0, 42, 3, 4)))
    }

    @Test
    fun testStreamNull() {
        val inputStream: InputStream? = null

        // Assert that the comparison fails when the input is not null
        assertFalse(inputStream.contentEquals(byteArrayOf(0, 1, 2, 3)))
        assertFalse(inputStream.contentEquals(emptyByteArray()))

        // Assert that the comparison succeeds when the byte array is also null
        assertTrue(inputStream.contentEquals(null))
    }

    @Test
    fun testBytesNull() {
        val inputStream = ByteArrayInputStream(byteArrayOf(0, 1, 2, 3))

        // Assert that the comparison fails when the input is null
        assertFalse(inputStream.contentEquals(null))
    }

    @Test
    fun testStreamEmpty() {
        // Assert that the comparison fails when the input is not empty or null
        assertFalse(ByteArrayInputStream(emptyByteArray()).contentEquals(byteArrayOf(0)))
        assertFalse(ByteArrayInputStream(emptyByteArray()).contentEquals(null))

        // Assert that the comparison succeeds when both the input and the provided bytes are empty
        assertTrue(ByteArrayInputStream(emptyByteArray()).contentEquals(emptyByteArray()))
    }

    @Test
    fun `read bytes into a byte array`() {
        val inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertContentEquals(byteArrayOf(1, 2, 3), inputStream.readByteArray(3))
        assertContentEquals(byteArrayOf(4, 5), inputStream.readByteArray(2))
        assertFailsWith<IOException> {
            inputStream.readByteArray(2)
        }
    }

    @Test
    fun `read utf8 string`() {
        val inputStream = ByteArrayInputStream("Hello world".toByteArray())
        assertEquals("Hello", inputStream.readUtf8String(5))
        assertEquals(" ", inputStream.readUtf8String(1))
        assertFailsWith<IOException> {
            assertEquals(" ", inputStream.readUtf8String(6))
        }
    }

    @Test
    fun `copy bytes from stream to buffer`() {
        val buffer = ByteArray(6)
        val count = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).copyTo(buffer, offset = 2, length = 3)

        assertContentEquals(
            byteArrayOf(0, 0, 1, 2, 3, 0),
            buffer,
        )
        assertEquals(3, count)
    }

    @Test
    fun `copy bytes from stream to buffer when buffer too short`() {
        val buffer = ByteArray(2)

        assertFailsWith<IndexOutOfBoundsException> {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).copyTo(buffer, offset = 2, length = 3)
        }
    }

    @Test
    fun `copy bytes from stream to buffer when input too short`() {
        val buffer = ByteArray(6)
        val count = ByteArrayInputStream(byteArrayOf(1, 2)).copyTo(buffer, offset = 2, length = 3)

        assertContentEquals(
            byteArrayOf(0, 0, 1, 2, 0, 0),
            buffer,
        )
        assertEquals(2, count)
    }
}
