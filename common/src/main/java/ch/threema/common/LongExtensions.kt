package ch.threema.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Long.toByteArray(order: ByteOrder): ByteArray =
    ByteBuffer.allocate(Long.SIZE_BYTES).order(order).putLong(this).array()

fun Long.toIntCapped(): Int =
    coerceIn(
        minimumValue = Int.MIN_VALUE.toLong(),
        maximumValue = Int.MAX_VALUE.toLong(),
    ).toInt()
