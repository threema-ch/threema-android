package ch.threema.common

import java.security.SecureRandom

private val secureRandom by lazy {
    SecureRandom()
}

/**
 * Returns a shared instance of [SecureRandom].
 * This should only be used in places where injection through Koin is not possible or too impractical.
 */
fun secureRandom(): SecureRandom =
    secureRandom

fun SecureRandom.generateRandomBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    nextBytes(bytes)
    return bytes
}

fun SecureRandom.nextULong(): ULong =
    nextLong().toULong()
