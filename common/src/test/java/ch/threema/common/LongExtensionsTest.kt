package ch.threema.common

import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals

class LongExtensionsTest {
    @Test
    fun `little-endian long to byte array`() {
        val long = "220f66cdff10a001".hexToLong()
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        assertContentEquals(bytes, long.toByteArray(order = ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `big-endian long to byte array`() {
        val long = "01a010ffcd660f22".hexToLong()
        val bytes = byteArrayOf(0x01, 0xa0.toByte(), 0x10.toByte(), 0xff.toByte(), 0xcd.toByte(), 0x66.toByte(), 0x0f.toByte(), 0x22.toByte())
        assertContentEquals(bytes, long.toByteArray(order = ByteOrder.BIG_ENDIAN))
    }
}
