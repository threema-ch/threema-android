package ch.threema.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Long.toByteArray(order: ByteOrder): ByteArray =
    ByteBuffer.allocate(Long.SIZE_BYTES).order(order).putLong(this).array()
