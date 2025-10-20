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

package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

sealed interface MasterKeyState {
    data class WithPassphrase(val protection: PassphraseProtection) : MasterKeyState {
        sealed interface PassphraseProtection {
            data class Version1(
                val protectedKey: CryptographicByteArray,
                val salt: CryptographicByteArray,
                val verification: CryptographicByteArray,
            ) : PassphraseProtection

            data class Version2(
                val argonVersion: Argon2Version,
                val encryptedData: CryptographicByteArray,
                val nonce: CryptographicByteArray,
                val memoryBytes: Int,
                val salt: CryptographicByteArray,
                val iterations: Int,
                val parallelism: Int,
            ) : PassphraseProtection {
                fun toOuterData() = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = argonVersion,
                    encryptedData = encryptedData,
                    nonce = nonce,
                    memoryBytes = memoryBytes,
                    salt = salt,
                    iterations = iterations,
                    parallelism = parallelism,
                )
            }
        }
    }

    data class WithRemoteSecret(
        val parameters: RemoteSecretParameters,
        val encryptedData: CryptographicByteArray,
    ) : MasterKeyState, WithoutPassphrase {
        override fun toInnerData() = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
            parameters = parameters,
            encryptedData = encryptedData,
        )
    }

    data class Plain(
        val masterKeyData: MasterKeyData,
        val wasMigrated: Boolean = false,
    ) : MasterKeyState, WithoutPassphrase {
        override fun toInnerData() = Version2MasterKeyStorageInnerData.Unprotected(
            masterKeyData = masterKeyData,
        )
    }

    sealed interface WithoutPassphrase : MasterKeyState {
        fun toInnerData(): Version2MasterKeyStorageInnerData
    }
}
