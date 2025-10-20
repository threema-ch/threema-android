/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.base.crypto

import ch.threema.base.utils.LoggingUtil
import ch.threema.libthreema.ChunkedXSalsa20Poly1305Decryptor
import ch.threema.libthreema.ChunkedXSalsa20Poly1305Encryptor
import ch.threema.libthreema.CryptoException
import ch.threema.libthreema.x25519DerivePublicKey
import ch.threema.libthreema.x25519Hsalsa20DeriveSharedSecret
import ch.threema.libthreema.xsalsa20Poly1305Decrypt
import ch.threema.libthreema.xsalsa20Poly1305Encrypt
import java.security.SecureRandom
import kotlin.experimental.xor

private val logger = LoggingUtil.getThreemaLogger("NaCl")

/**
 *  Libthreema bridge to the NaCl generated bindings.
 *
 *  @throws CryptoException if libthreema fails to derive the shared secret
 */
class NaCl(privateKey: ByteArray, publicKey: ByteArray) {

    @JvmField
    val sharedSecret: ByteArray = x25519Hsalsa20DeriveSharedSecret(
        publicKey = publicKey,
        secretKey = privateKey,
    )

    /**
     * Bridge over to implementation of `libthreema.xsalsa20Poly1305Encrypt`.
     *
     * @return The encrypted bytes
     * @throws CryptoException from libthreema
     */
    @Throws(CryptoException::class)
    fun encrypt(data: ByteArray, nonce: ByteArray): ByteArray {
        try {
            return xsalsa20Poly1305Encrypt(
                key = sharedSecret,
                nonce = nonce,
                data = data,
            )
        } catch (cryptoException: CryptoException) {
            logger.error("Encryption failed", cryptoException)
            throw cryptoException
        }
    }

    /**
     * Bridge over to implementation of `libthreema.xsalsa20Poly1305Decrypt`.
     *
     * @return The decrypted bytes
     * @throws CryptoException from libthreema
     */
    @Throws(CryptoException::class)
    fun decrypt(data: ByteArray, nonce: ByteArray): ByteArray {
        try {
            return xsalsa20Poly1305Decrypt(sharedSecret, nonce, data)
        } catch (cryptoException: CryptoException) {
            logger.error("Decryption failed", cryptoException)
            throw cryptoException
        }
    }

    companion object {

        const val PUBLIC_KEY_BYTES: Int = 32
        const val SECRET_KEY_BYTES: Int = 32
        const val SYMM_KEY_BYTES: Int = 32
        const val NONCE_BYTES: Int = 24

        private const val BOX_ZERO_BYTES: Int = 16
        private const val ZERO_BYTES: Int = 32
        const val BOX_OVERHEAD_BYTES: Int = ZERO_BYTES - BOX_ZERO_BYTES

        private const val IN_PLACE_ENCRYPTION_CHUNK_SIZE_BYTES = 1024 * 1024 // 1 MiB

        /**
         * Bridge over to implementation of `libthreema.xsalsa20Poly1305Encrypt`.
         *
         * @return The encrypted bytes
         * @throws CryptoException from libthreema
         */
        @JvmStatic
        @Throws(CryptoException::class)
        fun symmetricEncryptData(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
            try {
                return xsalsa20Poly1305Encrypt(
                    key = key,
                    nonce = nonce,
                    data = data,
                )
            } catch (cryptoException: CryptoException) {
                logger.error("Symmetric encryption failed", cryptoException)
                throw cryptoException
            }
        }

        /**
         * Bridge over to implementation of `libthreema.xsalsa20Poly1305Decrypt`.
         *
         * @return The decrypted bytes
         * @throws CryptoException from libthreema
         */
        @JvmStatic
        @Throws(CryptoException::class)
        fun symmetricDecryptData(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
            try {
                return xsalsa20Poly1305Decrypt(
                    key = key,
                    nonce = nonce,
                    data = data,
                )
            } catch (cryptoException: CryptoException) {
                logger.error("Symmetric decryption failed", cryptoException)
                throw cryptoException
            }
        }

        /**
         * In-place version of [symmetricEncryptData] that stores the output in the same byte array as the input.
         *
         * @param data      Plaintext message bytes starting at offset [BOX_OVERHEAD_BYTES]. Array contents will be altered to contain the created
         *                  tag (authentication code) and the encrypted bytes.
         *
         *                  In  = (empty tag bytes, 16) + (plain message bytes, N)
         *                  Out = (tag bytes, 16) + (encrypted message bytes, N)
         * @param key       Symmetric encryption key
         * @param nonce     Encryption nonce
         * @param chunkSize Amount of bytes passed to the libthreema implementation per iteration
         *
         * @throws IllegalArgumentException if the passed [data] is too short, or another parameter is not in correct form
         * @throws CryptoException          in all other error cases from libthreema
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class, CryptoException::class)
        fun symmetricEncryptDataInPlace(
            data: ByteArray,
            key: ByteArray,
            nonce: ByteArray,
            chunkSize: Int = IN_PLACE_ENCRYPTION_CHUNK_SIZE_BYTES,
        ) {
            require(data.size > BOX_OVERHEAD_BYTES) { "Invalid data length" }
            require(key.size == SYMM_KEY_BYTES) { "Invalid symmetric key length" }
            require(nonce.size == NONCE_BYTES) { "Invalid nonce length" }
            require(chunkSize > 0 && chunkSize % 2 == 0) { "Invalid chunk size value" }

            try {
                val encryptor = ChunkedXSalsa20Poly1305Encryptor(
                    key = key,
                    nonce = nonce,
                )

                var dataChunk: ByteArray
                var chunkStartIndex = BOX_OVERHEAD_BYTES
                while (chunkStartIndex <= data.lastIndex) {
                    val effectiveChunkSize = (data.size - chunkStartIndex).coerceAtMost(chunkSize)
                    dataChunk = data.copyOfRange(
                        fromIndex = chunkStartIndex,
                        toIndex = chunkStartIndex + effectiveChunkSize,
                    )
                    dataChunk = encryptor.encrypt(dataChunk)
                    // Replace the plain bytes in the input byte array with the encrypted bytes of this chunk
                    System.arraycopy(dataChunk, 0, data, chunkStartIndex, effectiveChunkSize)
                    chunkStartIndex += effectiveChunkSize
                }

                // Replace the tag (authentication code) bytes
                val tag: ByteArray = encryptor.finalize()
                System.arraycopy(tag, 0, data, 0, BOX_OVERHEAD_BYTES)
            } catch (cryptoException: CryptoException) {
                logger.error("Error while encrypting message data in-place", cryptoException)
                throw cryptoException
            }
        }

        /**
         * In-place version of [symmetricDecryptData] that stores the output in the same byte array as the input.
         *
         * Note that the last [BOX_OVERHEAD_BYTES] bytes should be ignored in the decrypted output, as they are just the tag (authentication code)
         * zeroized.
         *
         * @param data      The encrypted/decrypted bytes.
         *
         *                  In  = (tag bytes, 16) + (encrypted message bytes, N)
         *                  Out = (decrypted message bytes, N) + (zeroed tag bytes, 16)
         * @param key       Symmetric encryption key
         * @param nonce     Encryption nonce
         * @param chunkSize Amount of bytes passed to the libthreema implementation per iteration
         *
         * @return Decryption successful true/false
         *
         * @throws IllegalArgumentException if the tag (authentication code) verification failed (could happen if the [key] or [nonce] is incorrect),
         *                                  or another parameter is not in correct form
         * @throws CryptoException          in all other error cases from libthreema
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class, CryptoException::class)
        fun symmetricDecryptDataInPlace(
            data: ByteArray,
            key: ByteArray,
            nonce: ByteArray,
            chunkSize: Int = IN_PLACE_ENCRYPTION_CHUNK_SIZE_BYTES,
        ) {
            require(data.size > BOX_OVERHEAD_BYTES) { "Invalid data length" }
            require(key.size == SYMM_KEY_BYTES) { "Invalid symmetric key length" }
            require(nonce.size == NONCE_BYTES) { "Invalid nonce length" }
            require(chunkSize > 0 && chunkSize % 2 == 0) { "Invalid chunk size value" }

            val tag = data.copyOf(BOX_OVERHEAD_BYTES)

            val decryptor = try {
                ChunkedXSalsa20Poly1305Decryptor(
                    key = key,
                    nonce = nonce,
                ).also {
                    var dataChunk: ByteArray
                    var chunkStartIndex = BOX_OVERHEAD_BYTES
                    while (chunkStartIndex <= data.lastIndex) {
                        val effectiveChunkSize = (data.size - chunkStartIndex).coerceAtMost(chunkSize)
                        dataChunk = data.copyOfRange(
                            fromIndex = chunkStartIndex,
                            toIndex = chunkStartIndex + effectiveChunkSize,
                        )
                        dataChunk = it.decrypt(dataChunk)
                        // Replace the encrypted bytes in the input byte array with the decrypted bytes of this chunk.
                        // As the decrypted data will start at index 0 (replacing the tag bytes), we subtract BOX_OVERHEAD_BYTES from destPos.
                        System.arraycopy(dataChunk, 0, data, chunkStartIndex - BOX_OVERHEAD_BYTES, effectiveChunkSize)
                        chunkStartIndex += effectiveChunkSize
                    }
                }
            } catch (cryptoException: CryptoException) {
                logger.error("Error while decrypting message data in-place", cryptoException)
                throw cryptoException
            }
            try {
                decryptor.finalizeVerify(tag)
            } catch (cryptoException: CryptoException) {
                logger.error("Message tag verification failed - this may be due to a wrong key or nonce", cryptoException)
                throw cryptoException
            }

            // As we have shifted the decrypted bytes over to the left by BOX_OVERHEAD_BYTES, we have to clean up the last
            // BOX_OVERHEAD_BYTES (16) bytes.
            for (i in (data.size - BOX_OVERHEAD_BYTES) until data.size) {
                data[i] = 0
            }
        }

        /**
         *  Generate a random [KeyPair] with an optional [seed]
         *
         *  Bridge over to implementation of `libthreema.x25519DerivePublicKey`
         *
         *  @throws IllegalArgumentException if a seed is specified, but has the wrong length
         *  @throws CryptoException from libthreema
         */
        @JvmStatic
        @JvmOverloads
        @Throws(CryptoException::class, IllegalArgumentException::class)
        fun generateKeypair(seed: ByteArray? = null): KeyPair {
            if (seed != null && seed.size != SECRET_KEY_BYTES) {
                throw IllegalArgumentException("Seed must be exactly of length $SECRET_KEY_BYTES")
            }
            val privateKeyRandom = ByteArray(SECRET_KEY_BYTES)
            SecureRandom().nextBytes(privateKeyRandom)
            if (seed != null) {
                for (i in 0 until SECRET_KEY_BYTES) {
                    privateKeyRandom[i] = privateKeyRandom[i] xor seed[i]
                }
            }
            return KeyPair(
                publicKey = derivePublicKey(privateKey = privateKeyRandom),
                privateKey = privateKeyRandom,
            )
        }

        /**
         *  Generate a random keypair
         *
         *  Bridge over to implementation of `libthreema.x25519DerivePublicKey`
         *
         *  @param publicKey Will get filled with the derived public key bytes for the generated private key
         *  @param privateKey Will get filled with random bytes
         *
         *  @throws IllegalArgumentException When keys have bad lengths
         *  @throws CryptoException from [generateKeypair]
         *
         *  @see generateKeypair
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun generateKeypairInPlace(publicKey: ByteArray, privateKey: ByteArray) {
            require(publicKey.size == PUBLIC_KEY_BYTES) { "Public key must be of length $PUBLIC_KEY_BYTES" }
            require(privateKey.size == SECRET_KEY_BYTES) { "Private key must be of length $PUBLIC_KEY_BYTES" }
            generateKeypair().let { keypair ->
                keypair.publicKey.copyInto(publicKey)
                keypair.privateKey.copyInto(privateKey)
            }
        }

        /**
         * Bridge over to implementation of `libthreema.x25519DerivePublicKey`.
         *
         * @return The public key bytes
         * @throws CryptoException from libthreema
         */
        @JvmStatic
        @Throws(CryptoException::class)
        fun derivePublicKey(privateKey: ByteArray): ByteArray {
            try {
                return x25519DerivePublicKey(secretKey = privateKey)
            } catch (cryptoException: CryptoException) {
                logger.error("Failed to derive public key", cryptoException)
                throw cryptoException
            }
        }
    }
}
