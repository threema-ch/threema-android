/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.domain.stores

import ch.threema.base.ThreemaException
import ch.threema.domain.types.Identity

interface IdentityStore {
    /**
     * Encrypt the given plaintext with the private key stored in this identity store.
     *
     * @param plaintext         plaintext to be encrypted
     * @param nonce             nonce to be used for encryption (normally random)
     * @param receiverPublicKey public key of receiver
     * @return encrypted data (larger than input plaintext), or null if encryption failed
     */
    fun encryptData(plaintext: ByteArray, nonce: ByteArray, receiverPublicKey: ByteArray): ByteArray?

    /**
     * Decrypt the given plaintext with the private key stored in this identity store.
     *
     * @param ciphertext      ciphertext to be decrypted
     * @param nonce           nonce that was used for encryption
     * @param senderPublicKey public key of sender
     * @return decrypted data (smaller than input ciphertext), or null if decryption failed
     */
    fun decryptData(ciphertext: ByteArray, nonce: ByteArray, senderPublicKey: ByteArray): ByteArray?

    /**
     * Calculate the shared secret between the private key stored in this identity store
     * and the public key passed as a parameter.
     *
     * @param publicKey public key of other party
     * @return shared secret
     */
    fun calcSharedSecret(publicKey: ByteArray): ByteArray

    fun getIdentity(): Identity?

    fun getServerGroup(): String?

    fun getPublicKey(): ByteArray?

    fun getPrivateKey(): ByteArray?

    /**
     * Get the currently set nickname.
     * If no nickname is set, an empty string is returned.
     */
    fun getPublicNickname(): String

    /**
     * This method persists the public nickname. It does *not* reflect the changes and must
     * therefore only be used to persist the nickname.
     */
    fun setPublicNickname(publicNickname: String)

    /**
     * Store an identity in the identity store.
     *
     * @throws ThreemaException if the public key could not be derived
     */
    fun storeIdentity(
        identity: String,
        serverGroup: String,
        privateKey: ByteArray,
    )

    fun clear()
}
