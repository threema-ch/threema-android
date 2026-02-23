package ch.threema.common.models

import ch.threema.common.secureContentEquals

/**
 * A wrapper around a byte array which holds some cryptographic significance (e.g. an auth token, private key, password, hash, salt, etc.).
 * Using this class provides the following main benefits:
 * 1. since it implements its own equality, it can be used in data classes without needing to override their equals and hashCode, as would be
 * the case with a plain ByteArray
 * 2. protects the actual bytes from being accidentally logged or otherwise printed, due to the custom toString implementation
 */
open class CryptographicByteArray(val value: ByteArray) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CryptographicByteArray
        return value.secureContentEquals(other.value)
    }

    final override fun hashCode() = value.contentHashCode()

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        "[${value.size} bytes: ${value.joinToString(separator = ",", limit = 3) { it.toHexString() }}]"

    operator fun component1() = value
}
