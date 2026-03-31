package ch.threema.testhelpers

import app.cash.turbine.TurbineTestContext
import ch.threema.common.models.CryptographicByteArray
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * Generate an array of length `length` and fill it using a non-cryptographically-secure
 * random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun nonSecureRandomArray(length: Int): ByteArray {
    return Random.nextBytes(length)
}

fun cryptographicByteArrayOf(vararg bytes: Byte) = CryptographicByteArray(bytes)

/**
 * Generate a random Threema ID using a non-cryptographically-secure random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun randomIdentity(): String {
    val allowedChars = ('A'..'Z') + ('0'..'9')
    return (1..8)
        .map { allowedChars.random() }
        .joinToString("")
}

@Suppress("FunctionName")
fun MUST_NOT_BE_CALLED(): Nothing {
    throw UnsupportedOperationException("This method must not be called")
}

fun Any.loadResource(file: String): String =
    loadResourceAsBytes(file).toString(Charsets.UTF_8)

fun Any.loadResourceAsBytes(file: String): ByteArray =
    (javaClass.classLoader.getResourceAsStream(file) ?: error("Resource file '$file' not found"))
        .use {
            it.readBytes()
        }

suspend fun <T> TurbineTestContext<T>.expectItem(expected: T) {
    assertEquals(expected, awaitItem())
}

fun createTempDirectory(prefix: String = "test"): File {
    val directory = File.createTempFile(prefix, "test")
    directory.delete()
    directory.mkdirs()
    return directory
}
