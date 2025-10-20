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
import org.saltyrtc.client.crypto.CryptoException
import org.saltyrtc.client.crypto.CryptoInstance

/**
 *  @throws CryptoException In case no NaCl instance could be instantiated
 */
@AnyThread
class LibthreemaNaClCryptoInstance internal constructor(
    ownPrivateKey: ByteArray,
    otherPublicKey: ByteArray,
) : CryptoInstance {

    private val nacl: NaCl =
        runCatching {
            NaCl(ownPrivateKey, otherPublicKey)
        }.getOrElse { throwable ->
            throw CryptoException("Could not create NaCl instance", throwable)
        }

    @Throws(CryptoException::class)
    override fun encrypt(data: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            nacl.encrypt(
                data = data,
                nonce = nonce,
            )
        }.getOrElse { cause ->
            throw CryptoException("Could not encrypt data", cause)
        }

    @Throws(CryptoException::class)
    override fun decrypt(data: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            nacl.decrypt(
                data = data,
                nonce = nonce,
            )
        }.getOrElse { throwable ->
            throw CryptoException("Could not decrypt data", throwable)
        }
}
