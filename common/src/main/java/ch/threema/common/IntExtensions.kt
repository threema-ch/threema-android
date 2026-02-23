package ch.threema.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Int.toByteArray(order: ByteOrder): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).order(order).putInt(this).array()

/**
 * Returns the next higher power of 2, or the number itself it if is already a power of 2.
 * Must not be used for negative numbers.
 */
fun Int.roundUpToPowerOfTwo(): Int {
    var result = this
    result--
    result = result or (result shr 1)
    result = result or (result shr 2)
    result = result or (result shr 4)
    result = result or (result shr 8)
    result = result or (result shr 16)
    result++
    return result
}
