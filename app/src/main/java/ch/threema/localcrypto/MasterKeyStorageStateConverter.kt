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

import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyState.WithPassphrase.PassphraseProtection
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import java.io.IOException

private val logger = getThreemaLogger("MasterKeyStorageStateConverter")

/**
 * Can convert back and forth between [MasterKeyState] and [MasterKeyStorageData] formats, where the former is
 * meant to be as storage-version-agnostic as possible while the latter is a representation of how the key data is stored.
 */
class MasterKeyStorageStateConverter(
    private val version1KeyVerifier: Version1MasterKeyCrypto,
) {
    fun toKeyState(storageData: MasterKeyStorageData): MasterKeyState =
        when (storageData) {
            is MasterKeyStorageData.Version1 -> when (val data = storageData.data) {
                is Version1MasterKeyStorageData.PassphraseProtected -> {
                    MasterKeyState.WithPassphrase(
                        protection = PassphraseProtection.Version1(
                            protectedKey = data.protectedKey,
                            salt = data.salt,
                            verification = data.verification,
                        ),
                    )
                }
                is Version1MasterKeyStorageData.Unprotected -> {
                    if (!version1KeyVerifier.checkVerification(data.masterKeyData, data.verification)) {
                        throw IOException("Corrupt version 1 key")
                    }
                    logger.info("Migrating unprotected master key from version 1 to version 2")
                    MasterKeyState.Plain(
                        masterKeyData = data.masterKeyData,
                        wasMigrated = true,
                    )
                }
            }
            is MasterKeyStorageData.Version2 -> {
                toKeyState(storageData.outerData)
            }
        }

    fun toKeyState(outerData: Version2MasterKeyStorageOuterData): MasterKeyState =
        when (outerData) {
            is Version2MasterKeyStorageOuterData.NotPassphraseProtected -> {
                toKeyState(outerData.innerData)
            }
            is Version2MasterKeyStorageOuterData.PassphraseProtected -> {
                toKeyState(outerData)
            }
        }

    fun toKeyState(outerData: Version2MasterKeyStorageOuterData.PassphraseProtected): MasterKeyState.WithPassphrase =
        MasterKeyState.WithPassphrase(
            protection = PassphraseProtection.Version2(
                argonVersion = outerData.argonVersion,
                encryptedData = outerData.encryptedData,
                nonce = outerData.nonce,
                memoryBytes = outerData.memoryBytes,
                salt = outerData.salt,
                iterations = outerData.iterations,
                parallelism = outerData.parallelism,
            ),
        )

    fun toKeyState(innerData: Version2MasterKeyStorageInnerData): MasterKeyState.WithoutPassphrase =
        when (innerData) {
            is Version2MasterKeyStorageInnerData.RemoteSecretProtected -> {
                toKeyState(innerData)
            }

            is Version2MasterKeyStorageInnerData.Unprotected -> {
                MasterKeyState.Plain(
                    masterKeyData = innerData.masterKeyData,
                )
            }
        }

    fun toKeyState(innerData: Version2MasterKeyStorageInnerData.RemoteSecretProtected): MasterKeyState.WithRemoteSecret =
        MasterKeyState.WithRemoteSecret(
            parameters = innerData.parameters,
            encryptedData = innerData.encryptedData,
        )

    fun toStorageData(keyState: MasterKeyState): MasterKeyStorageData.Version2 =
        when (keyState) {
            is MasterKeyState.WithPassphrase -> toStorageData(keyState)
            is MasterKeyState.WithRemoteSecret -> toStorageData(keyState)
            is MasterKeyState.Plain -> toStorageData(keyState)
        }

    fun toStorageData(keyState: MasterKeyState.WithPassphrase) =
        when (val protection = keyState.protection) {
            is PassphraseProtection.Version1 -> {
                error("Version 1 passphrase can not be stored, must be migrated")
            }
            is PassphraseProtection.Version2 -> {
                MasterKeyStorageData.Version2(
                    outerData = protection.toOuterData(),
                )
            }
        }

    fun toStorageData(keyState: MasterKeyState.WithRemoteSecret) =
        MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = keyState.toInnerData(),
            ),
        )

    fun toStorageData(keyState: MasterKeyState.Plain) =
        MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = keyState.toInnerData(),
            ),
        )
}
