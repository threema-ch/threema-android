package ch.threema.common

import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ShortExtensionsTest {
    @Test
    fun `short to little-endian`() {
        val short = 0xaffe.toShort()
        val bytes = short.toByteArray(order = ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(
            byteArrayOf(0xfe.toByte(), 0xaf.toByte()),
            bytes,
        )
    }

    @Test
    fun `short to big-endian`() {
        val short = 0xaffe.toShort()
        val bytes = short.toByteArray(order = ByteOrder.BIG_ENDIAN)
        assertContentEquals(
            byteArrayOf(0xaf.toByte(), 0xfe.toByte()),
            bytes,
        )
    }
}
