package ch.threema.common

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class OutputStreamExtensionsTest {
    @Test
    fun `write little-endian integer`() {
        val outputStream = ByteArrayOutputStream()
        outputStream.writeLittleEndianInt(0x1234f045)
        outputStream.writeLittleEndianInt(0x1afbfcfd)
        assertContentEquals(
            byteArrayOf(0x45, 0xf0.toByte(), 0x34, 0x12, 0xfd.toByte(), 0xfc.toByte(), 0xfb.toByte(), 0x1a),
            outputStream.toByteArray(),
        )
    }

    @Test
    fun `write little-endian short`() {
        val outputStream = ByteArrayOutputStream()
        outputStream.writeLittleEndianShort(0xf045.toShort())
        outputStream.writeLittleEndianShort(0x1234.toShort())
        assertContentEquals(
            byteArrayOf(0x45, 0xf0.toByte(), 0x34, 0x12),
            outputStream.toByteArray(),
        )
    }
}
