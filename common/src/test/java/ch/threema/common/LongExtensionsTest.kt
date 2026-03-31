package ch.threema.common

import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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

    @Test
    fun `toIntCapped caps long at lower end`() {
        val longValue: Long = Int.MIN_VALUE - 1L
        assertEquals(Int.MIN_VALUE, longValue.toIntCapped())
    }

    @Test
    fun `toIntCapped caps long at upper end`() {
        val longValue: Long = Int.MAX_VALUE + 1L
        assertEquals(Int.MAX_VALUE, longValue.toIntCapped())
    }

    @Test
    fun `toIntCapped should have same numeric value as long value`() {
        val longValueBig: Long = Int.MAX_VALUE - 1L
        assertEquals(Int.MAX_VALUE - 1, longValueBig.toIntCapped())

        val longValueSmall: Long = Int.MIN_VALUE + 1L
        assertEquals(Int.MIN_VALUE + 1, longValueSmall.toIntCapped())

        val longValueEdge: Long = Int.MAX_VALUE.toLong()
        assertEquals(Int.MAX_VALUE, longValueEdge.toIntCapped())

        val langValueIntMin: Long = Int.MIN_VALUE.toLong()
        assertEquals(Int.MIN_VALUE, langValueIntMin.toIntCapped())
    }
}
