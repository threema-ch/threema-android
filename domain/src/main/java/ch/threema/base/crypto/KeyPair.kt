package ch.threema.base.crypto

import ch.threema.common.secureContentEquals

data class KeyPair(
    @JvmField val publicKey: ByteArray,
    @JvmField val privateKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KeyPair
        if (!publicKey.secureContentEquals(other.publicKey)) return false
        if (!privateKey.secureContentEquals(other.privateKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }

    override fun toString() = "KeyPair"
}
