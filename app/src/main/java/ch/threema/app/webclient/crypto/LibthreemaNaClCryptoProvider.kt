/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.webclient.crypto

import androidx.annotation.AnyThread
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.LoggingUtil
import org.saltyrtc.client.crypto.CryptoException
import org.saltyrtc.client.crypto.CryptoInstance
import org.saltyrtc.client.crypto.CryptoProvider

private val logger = LoggingUtil.getThreemaLogger("LibthreemaNaClCryptoProvider")

/**
 *  A bridge to the NaCl implementation from Libthreema.
 *
 *  Mapping every possible exception to [org.saltyrtc.client.crypto.CryptoException] in order to match [CryptoProvider]s behaviour.
 */
@AnyThread
class LibthreemaNaClCryptoProvider : CryptoProvider {

    @Throws(CryptoException::class)
    override fun getInstance(ownPrivateKey: ByteArray, otherPublicKey: ByteArray): CryptoInstance {
        logger.debug("getInstance")
        return runCatching {
            LibthreemaNaClCryptoInstance(ownPrivateKey, otherPublicKey)
        }.getOrElse {
            throw CryptoException("Failed to create ")
        }
    }

    @Throws(CryptoException::class)
    override fun generateKeypair(publickey: ByteArray, privatekey: ByteArray) {
        logger.debug("generateKeypair")
        try {
            NaCl.generateKeypairInPlace(publickey, privatekey)
        } catch (error: Error) {
            throw CryptoException("Failed to generate a keypair: $error", error)
        }
    }

    @Throws(CryptoException::class)
    override fun derivePublicKey(privateKey: ByteArray): ByteArray {
        logger.debug("derivePublicKey")
        return runCatching {
            NaCl.derivePublicKey(privateKey)
        }.getOrElse { throwable ->
            throw CryptoException("Deriving public key from private key failed: $throwable", throwable)
        }
    }

    @Throws(CryptoException::class)
    override fun symmetricEncrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            NaCl.symmetricEncryptData(
                data = data,
                key = key,
                nonce = nonce,
            )
        }.getOrElse { throwable ->
            throw CryptoException("Could not encrypt data: $throwable", throwable)
        }

    @Throws(CryptoException::class)
    override fun symmetricDecrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            NaCl.symmetricDecryptData(
                data = data,
                key = key,
                nonce = nonce,
            )
        }.getOrElse { throwable ->
            throw CryptoException("Could not decrypt data", throwable)
        }
}
