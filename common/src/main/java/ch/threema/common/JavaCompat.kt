package ch.threema.common

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A collection of helper functions to allow using functions from the Kotlin standard library also in Java.
 */
object JavaCompat {
    @JvmStatic
    fun inputStreamToString(inputStream: InputStream): String =
        inputStream.readBytes().toString(charset = Charsets.UTF_8)

    @JvmStatic
    fun stringToInputStream(string: String): InputStream =
        string.byteInputStream()

    @JvmStatic
    @Throws(IOException::class)
    @JvmOverloads
    fun copyStream(inputStream: InputStream, outputStream: OutputStream, bufferSize: Int = 8 * 1024): Long =
        inputStream.copyTo(outputStream, bufferSize)

    @JvmStatic
    fun readBytes(inputStream: InputStream): ByteArray =
        inputStream.readBytes()

    @JvmStatic
    fun deleteRecursively(directory: File) {
        directory.deleteRecursively()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readBytes(file: File): ByteArray =
        file.readBytes()
}
