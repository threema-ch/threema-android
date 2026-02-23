package ch.threema.common

import java.io.InputStream

/**
 * A collection of helper functions to allow using functions from the Kotlin standard library also in Java.
 */
object JavaCompat {
    @JvmStatic
    fun readBytes(inputStream: InputStream): ByteArray =
        inputStream.readBytes()

    @JvmStatic
    fun getStackTraceString(e: Throwable): String =
        e.stackTraceToString()
}
