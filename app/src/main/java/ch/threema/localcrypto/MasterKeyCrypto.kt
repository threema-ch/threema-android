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

import ch.threema.base.utils.LoggingUtil
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters

private val logger = LoggingUtil.getThreemaLogger("MasterKeyCrypto")

class MasterKeyCrypto(
    private val converter: MasterKeyStorageStateConverter = MasterKeyStorageStateConverter(),
    private val version2Crypto: Version2MasterKeyCrypto = Version2MasterKeyCrypto(),
    private val version1Crypto: Version1MasterKeyCrypto = Version1MasterKeyCrypto,
) {
    fun verifyPassphrase(keyState: MasterKeyState.WithPassphrase, passphrase: CharArray): Boolean =
        when (val protection = keyState.protection) {
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version1 -> {
                val masterKeyData = version1Crypto.decryptPassphraseProtectedMasterKey(protection, passphrase)
                version1Crypto.checkVerification(masterKeyData, protection.verification)
            }
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version2 -> {
                try {
                    version2Crypto.decryptWithPassphrase(protection.toOuterData(), passphrase)
                    true
                } catch (e: CryptoException) {
                    logger.warn("Check passphrase failed due to wrong passphrase", e)
                    false
                }
            }
        }

    @Throws(CryptoException::class)
    fun decryptWithPassphrase(
        keyState: MasterKeyState.WithPassphrase,
        passphrase: CharArray,
    ): MasterKeyState.WithoutPassphrase {
        when (val protection = keyState.protection) {
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version1 -> {
                val masterKeyData = version1Crypto.decryptPassphraseProtectedMasterKey(protection, passphrase)
                if (!version1Crypto.checkVerification(masterKeyData, protection.verification)) {
                    logger.warn("Failed to unlock with version 1 passphrase due to wrong passphrase")
                    throw CryptoException()
                }
                return MasterKeyState.Plain(masterKeyData, wasMigrated = true)
            }
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version2 -> {
                val innerData = version2Crypto.decryptWithPassphrase(protection.toOuterData(), passphrase)
                return converter.toKeyState(innerData)
            }
        }
    }

    @Throws(CryptoException::class)
    fun encryptWithPassphrase(
        keyState: MasterKeyState.WithoutPassphrase,
        passphrase: CharArray,
    ): MasterKeyState.WithPassphrase =
        converter.toKeyState(
            version2Crypto.encryptWithPassphrase(keyState.toInnerData(), passphrase),
        )

    @Throws(CryptoException::class)
    fun decryptWithRemoteSecret(
        keyState: MasterKeyState.WithRemoteSecret,
        remoteSecret: RemoteSecret,
    ): MasterKeyState.Plain {
        val unprotected = version2Crypto.decryptWithRemoteSecret(remoteSecret, keyState.toInnerData())
        return MasterKeyState.Plain(unprotected.masterKeyData)
    }

    @Throws(CryptoException::class)
    fun encryptWithRemoteSecret(
        keyState: MasterKeyState.Plain,
        remoteSecret: RemoteSecret,
        parameters: RemoteSecretParameters,
    ): MasterKeyState.WithRemoteSecret =
        converter.toKeyState(
            version2Crypto.encryptWithRemoteSecret(remoteSecret, parameters, keyState.toInnerData()),
        )
}
