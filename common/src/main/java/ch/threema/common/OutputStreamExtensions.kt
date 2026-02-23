package ch.threema.common

import java.io.IOException
import java.io.OutputStream

@Throws(IOException::class)
fun OutputStream.writeLittleEndianShort(value: Short) {
    write(((value.toInt() shr 0) and 0xff).toByte().toInt())
    write(((value.toInt() shr 8) and 0xff).toByte().toInt())
}

@Throws(IOException::class)
fun OutputStream.writeLittleEndianInt(value: Int) {
    write(((value shr 0) and 0xff).toByte().toInt())
    write(((value shr 8) and 0xff).toByte().toInt())
    write(((value shr 16) and 0xff).toByte().toInt())
    write(((value shr 24) and 0xff).toByte().toInt())
}
