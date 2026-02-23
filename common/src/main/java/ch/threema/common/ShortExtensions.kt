package ch.threema.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Short.toByteArray(order: ByteOrder): ByteArray =
    ByteBuffer.allocate(Short.SIZE_BYTES).order(order).putShort(this).array()
