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

package ch.threema.localcrypto

import ch.threema.common.models.CryptographicByteArray
import ch.threema.common.secureContentEquals
import ch.threema.common.xor
import ch.threema.domain.libthreema.LibthreemaJavaBridge.createScryptParameters
import ch.threema.libthreema.scrypt
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import java.security.MessageDigest

class Version1MasterKeyCrypto {
    fun checkVerification(masterKeyData: MasterKeyData, verification: CryptographicByteArray): Boolean =
        calculateVerification(masterKeyData).secureContentEquals(verification.value)

    private fun calculateVerification(masterKeyData: MasterKeyData): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(masterKeyData.value)
        return messageDigest.digest().copyOfRange(0, MasterKeyConfig.VERSION1_VERIFICATION_LENGTH)
    }

    fun decryptPassphraseProtectedMasterKey(
        protection: MasterKeyState.WithPassphrase.PassphraseProtection.Version1,
        passphrase: CharArray,
    ): MasterKeyData {
        val passphraseKey = deriveVersion1PassphraseKey(passphrase, protection.salt)
        return MasterKeyData(protection.protectedKey.value xor passphraseKey)
    }

    private fun deriveVersion1PassphraseKey(
        passphrase: CharArray,
        salt: CryptographicByteArray,
    ): ByteArray =
        scrypt(
            password = String(passphrase).toByteArray(),
            salt = salt.value,
            parameters = createScryptParameters(outputLength = MasterKeyConfig.KEY_LENGTH.toByte()),
        )
}
