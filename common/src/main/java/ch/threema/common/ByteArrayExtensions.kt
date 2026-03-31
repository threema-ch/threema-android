package ch.threema.common

import ch.threema.common.models.CryptographicByteArray
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.experimental.xor
import kotlin.text.toHexString as toHexStringKotlinStdLib

fun emptyByteArray() = ByteArray(0)

fun buildByteArray(initialSize: Int = 32, block: ByteArrayOutputStream.() -> Unit): ByteArray =
    ByteArrayOutputStream(initialSize)
        .apply(block)
        .toByteArray()

infix fun ByteArray.xor(other: ByteArray): ByteArray {
    require(size == other.size)
    val processedData = ByteArray(size)
    for (i in indices) {
        processedData[i] = this[i] xor other[i]
    }
    return processedData
}

fun ByteArray.secureContentEquals(other: ByteArray) =
    MessageDigest.isEqual(this, other)

fun ByteArray.toCryptographicByteArray() = CryptographicByteArray(this)

fun ByteArray.chunked(size: Int): List<ByteArray> = asIterable()
    .chunked(size)
    .map { it.toByteArray() }

/**
 * Returns a string representation of this [ByteArray] with lowercase hex characters.
 *
 * @param maxBytes if set to a positive value smaller than the size of the array only [maxBytes] bytes
 *  will be visible in the string representation followed by an ellipsis character.
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmOverloads
fun ByteArray.toHexString(maxBytes: Int = 0): String =
    if (maxBytes in 1 until size) {
        val bytes = copyOfRange(0, maxBytes)
        "${bytes.toHexStringKotlinStdLib()}$HORIZONTAL_ELLIPSIS"
    } else {
        toHexStringKotlinStdLib()
    }

private const val HORIZONTAL_ELLIPSIS = 0x2026.toChar()

fun ByteArray.toLong(order: ByteOrder): Long =
    ByteBuffer.wrap(this).order(order).long

fun ByteArray.readLittleEndianInt(offset: Int): Int =
    ((this[offset + 0].toInt() and 0xff) shl 0) +
        ((this[offset + 1].toInt() and 0xff) shl 8) +
        ((this[offset + 2].toInt() and 0xff) shl 16) +
        ((this[offset + 3].toInt() and 0xff) shl 24)

fun ByteArray.readLittleEndianShort(offset: Int): Short =
    (
        ((this[offset + 0].toInt() and 0xff) shl 0) +
            ((this[offset + 1].toInt() and 0xff) shl 8)
        ).toShort()
