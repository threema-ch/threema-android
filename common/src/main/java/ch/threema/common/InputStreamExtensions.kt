package ch.threema.common

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

fun InputStream?.orEmpty(): InputStream =
    this ?: emptyInputStream()

fun emptyInputStream(): InputStream =
    emptyByteArray().inputStream()

/**
 * Compares the content of the input stream with the provided [byteArray]. Note that the input stream is read until the first byte that does not match
 * the byte array.
 *
 * @return true if both the input stream and the byte array are null or both contain the exact same bytes
 */
fun InputStream?.contentEquals(byteArray: ByteArray?): Boolean {
    if (this == null && byteArray == null) {
        return true
    }
    if (this == null || byteArray == null) {
        return false
    }
    use { input ->
        var index = 0
        var next: Int
        while (input.read().also { next = it } != -1) {
            // Abort if the provided byte array is shorter than the input stream
            if (index >= byteArray.size) {
                return false
            }
            // Abort if the next byte does not match the byte array's byte at this position
            if (next.toByte() != byteArray[index]) {
                return false
            }
            index++
        }
        return index == byteArray.size
    }
}

/**
 * Reads the next [size] bytes and copies them into a ByteArray, which is then returned.
 * @throws IOException If reading fails or if there are not enough bytes.
 */
@Throws(IOException::class)
fun InputStream.readByteArray(size: Int): ByteArray {
    val byteArray = ByteArray(size)
    val bytesRead = read(byteArray)
    if (bytesRead != size) {
        throw IOException("Expected $size bytes but only $bytesRead were read")
    }
    return byteArray
}

/**
 * Reads the next [bytes] bytes and interprets them as a UTF-8 string
 * @throws IOException If reading fails or if there are not enough bytes.
 */
@Throws(IOException::class)
fun InputStream.readUtf8String(bytes: Int): String =
    String(readByteArray(bytes))

@Throws(IOException::class)
fun InputStream.readLittleEndianInt(): Int {
    val byte1 = readOrThrow()
    val byte2 = readOrThrow()
    val byte3 = readOrThrow()
    val byte4 = readOrThrow()
    return ((byte1 and 0xff) shl 0) +
        ((byte2 and 0xff) shl 8) +
        ((byte3 and 0xff) shl 16) +
        ((byte4 and 0xff) shl 24)
}

@Throws(IOException::class)
fun InputStream.readLittleEndianShort(): Short {
    val byte1 = readOrThrow()
    val byte2 = readOrThrow()
    return (
        ((byte1 and 0xff) shl 0) +
            ((byte2 and 0xff) shl 8)
        ).toShort()
}

@Throws(IOException::class)
private fun InputStream.readOrThrow(): Int {
    val value = read()
    if (value == -1) {
        throw EOFException()
    }
    return value
}

/**
 * Copies [length] bytes from the input stream into [buffer] at [offset], or fewer if not enough bytes are available.
 *
 * @return the number of bytes actually copied
 * @throws IndexOutOfBoundsException if [buffer] is not large enough to hold the copied bytes
 */
@Throws(IOException::class)
fun InputStream.copyTo(buffer: ByteArray, offset: Int, length: Int): Int {
    require(length >= 0)
    var remaining = length
    while (remaining > 0) {
        val location = length - remaining
        val count = read(buffer, offset + location, remaining)
        if (count == -1) {
            break
        }
        remaining -= count
    }
    return length - remaining
}
