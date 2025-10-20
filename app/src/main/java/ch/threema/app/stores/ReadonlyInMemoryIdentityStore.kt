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

package ch.threema.app.stores

import ch.threema.base.crypto.NaCl
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity

// TODO(ANDR-4067): This class should be removed
@Deprecated("Do not use this class, it only exists as a workaround")
class ReadonlyInMemoryIdentityStore(
    private val identity: Identity,
    private val serverGroup: String,
    private val privateKey: ByteArray,
) : IdentityStore {
    override fun getIdentity() = identity

    override fun getServerGroup() = serverGroup

    override fun getPublicKey(): ByteArray? {
        throw NotImplementedError()
    }

    override fun getPrivateKey() = privateKey

    override fun getPublicNickname() = identity

    override fun calcSharedSecret(publicKey: ByteArray): ByteArray =
        NaCl(privateKey, publicKey).sharedSecret

    override fun encryptData(plaintext: ByteArray, nonce: ByteArray, receiverPublicKey: ByteArray): ByteArray? {
        throw NotImplementedError()
    }

    override fun decryptData(ciphertext: ByteArray, nonce: ByteArray, senderPublicKey: ByteArray): ByteArray? {
        throw NotImplementedError()
    }

    override fun setPublicNickname(publicNickname: String) {
        throw NotImplementedError()
    }

    override fun storeIdentity(identity: String, serverGroup: String, privateKey: ByteArray) {
        throw NotImplementedError()
    }

    override fun clear() {
        throw NotImplementedError()
    }
}
