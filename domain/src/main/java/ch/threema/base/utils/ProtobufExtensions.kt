package ch.threema.base.utils

import ch.threema.common.models.CryptographicByteArray
import ch.threema.common.secureRandom
import ch.threema.common.toCryptographicByteArray
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import java.security.SecureRandom

fun CryptographicByteArray.toByteString() =
    value.toByteString()

fun ByteString.toCryptographicByteArray() =
    toByteArray().toCryptographicByteArray()

/**
 * Creates between 0 (inclusive) and 256 (exclusive) bytes of PKCS#7 style padding.
 * Each byte of the padding has the length of the padding as value.
 */
fun generateRandomProtobufPadding(secureRandom: SecureRandom = secureRandom()): ByteString {
    val paddingLength = secureRandom.nextInt(256)
    val paddingValue = paddingLength.toByte()
    return ByteArray(paddingLength) { paddingValue }.toByteString()
}
