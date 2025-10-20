package ch.threema.protobuf

import ch.threema.common.secureContentEquals
import com.google.protobuf.kotlin.toByteString

fun combineEncryptedDataAndNonce(data: ByteArray, nonce: ByteArray): ByteArray =
    encryptedDataWithNonceAhead {
        this.nonce = nonce.toByteString()
        this.data = data.toByteString()
    }
        .toByteArray()

fun separateEncryptedDataAndNonce(combined: ByteArray): EncryptedDataAndNonce {
    val dataAndNonce = EncryptedDataWithNonceAhead.parseFrom(combined)
    return EncryptedDataAndNonce(
        data = dataAndNonce.data.toByteArray(),
        nonce = dataAndNonce.nonce.toByteArray(),
    )
}

data class EncryptedDataAndNonce(val data: ByteArray, val nonce: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedDataAndNonce
        if (!data.secureContentEquals(other.data)) return false
        if (!nonce.secureContentEquals(other.nonce)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}
