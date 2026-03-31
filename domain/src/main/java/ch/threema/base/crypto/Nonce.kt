package ch.threema.base.crypto

import ch.threema.domain.types.IdentityString
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A nonce that consists of [NaCl.NONCE_BYTES] bytes.
 *
 * @throws IllegalArgumentException if [bytes] does not contain [NaCl.NONCE_BYTES] bytes
 */
@JvmInline
value class Nonce(val bytes: ByteArray) {
    init {
        require(bytes.size == NaCl.NONCE_BYTES)
    }

    /**
     * Hash nonce with HMAC-SHA256 using the identity as the key.
     *
     * This serves to make it impossible to correlate the nonce DBs of users to
     * determine whether they have been communicating.
     *
     * @throws NoSuchAlgorithmException if the algorithm is not available on the device
     * @throws InvalidKeyException if the [identity] is not suitable as key
     */
    fun hashNonce(identity: IdentityString) = HashedNonce.getFromNonce(this, identity)
}

/**
 * A nonce hashed with HMAC-SHA256 using the identity of the user as key.
 *
 * @throws IllegalArgumentException if [bytes] does not contain 32 bytes
 */
@JvmInline
value class HashedNonce(val bytes: ByteArray) {
    init {
        require(bytes.size == 32)
    }

    companion object {
        /**
         * Hash the given [nonce] with HMAC-SHA256 using [identity] as key.
         *
         * @throws NoSuchAlgorithmException if the algorithm is not available on the device
         * @throws InvalidKeyException if the [identity] is not suitable as key
         */
        fun getFromNonce(nonce: Nonce, identity: IdentityString): HashedNonce {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(identity.encodeToByteArray(), "HmacSHA256"))
            return HashedNonce(mac.doFinal(nonce.bytes))
        }
    }
}
