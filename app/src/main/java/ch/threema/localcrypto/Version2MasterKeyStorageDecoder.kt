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

import ch.threema.base.utils.toCryptographicByteArray
import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretHash
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.localcrypto.protobuf.InnerKeyStorage
import ch.threema.localcrypto.protobuf.InnerKeyStorageV1
import ch.threema.localcrypto.protobuf.IntermediateKeyStorage
import ch.threema.localcrypto.protobuf.IntermediateKeyStorageV1
import ch.threema.localcrypto.protobuf.OuterKeyStorage
import ch.threema.localcrypto.protobuf.OuterKeyStorageV1
import ch.threema.localcrypto.protobuf.OuterKeyStorageV1.Argon2idProtected
import ch.threema.protobuf.separateEncryptedDataAndNonce
import java.io.DataInputStream

class Version2MasterKeyStorageDecoder {
    fun decodeOuterKeyStorage(inputStream: DataInputStream): MasterKeyStorageData.Version2 {
        val outerKeyStorageVersionNumber = inputStream.readUnsignedShort()

        when (OuterKeyStorage.Version.forNumber(outerKeyStorageVersionNumber)) {
            OuterKeyStorage.Version.V1_0 -> {
                return decodeOuterKeyStorageV1(inputStream)
            }
            else -> error("Unsupported outer version number $outerKeyStorageVersionNumber")
        }
    }

    private fun decodeOuterKeyStorageV1(dataInputStream: DataInputStream): MasterKeyStorageData.Version2 {
        val outerKeyStorage = OuterKeyStorageV1.parseFrom(dataInputStream)
        return when {
            outerKeyStorage.hasArgon2IdProtectedIntermediate() -> {
                val argon2idProtected = outerKeyStorage.argon2IdProtectedIntermediate!!

                val encryptedDataAndNonce = separateEncryptedDataAndNonce(argon2idProtected.encryptedIntermediate.toByteArray())
                MasterKeyStorageData.Version2(
                    outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                        argonVersion = when (argon2idProtected.version) {
                            Argon2idProtected.Argon2Version.VERSION_1_3 -> Argon2Version.VERSION_1_3
                            else -> error("Unsupported argon2 version ${argon2idProtected.version}")
                        },
                        encryptedData = encryptedDataAndNonce.data.toCryptographicByteArray(),
                        nonce = encryptedDataAndNonce.nonce.toCryptographicByteArray(),
                        memoryBytes = argon2idProtected.memoryBytes,
                        salt = argon2idProtected.salt.toCryptographicByteArray(),
                        iterations = argon2idProtected.iterations,
                        parallelism = argon2idProtected.parallelism,
                    ),
                )
            }
            outerKeyStorage.hasPlaintextIntermediate() -> {
                outerKeyStorage.plaintextIntermediate!!
                    .newInput()
                    .let(::DataInputStream)
                    .use { intermediateKeyStorageStream ->
                        MasterKeyStorageData.Version2(
                            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                                innerData = decodeIntermediateKeyStorage(intermediateKeyStorageStream),
                            ),
                        )
                    }
            }
            else -> error("Invalid outer key storage")
        }
    }

    fun decodeIntermediateKeyStorage(dataInputStream: DataInputStream): Version2MasterKeyStorageInnerData {
        val intermediateKeyStorageVersionNumber = dataInputStream.readUnsignedShort()
        return when (IntermediateKeyStorage.Version.forNumber(intermediateKeyStorageVersionNumber)) {
            IntermediateKeyStorage.Version.V1_0 -> {
                decodeIntermediateKeyStorageV1(dataInputStream)
            }
            else -> error("Unsupported intermediate version number $intermediateKeyStorageVersionNumber")
        }
    }

    private fun decodeIntermediateKeyStorageV1(dataInputStream: DataInputStream): Version2MasterKeyStorageInnerData {
        val intermediateKeyStorage = IntermediateKeyStorageV1.parseFrom(dataInputStream)
        return when {
            intermediateKeyStorage.hasPlaintextInner() -> {
                intermediateKeyStorage.plaintextInner!!
                    .newInput()
                    .let(::DataInputStream)
                    .let { innerKeyStorageStream ->
                        decodeInnerKeyStorage(innerKeyStorageStream)
                    }
            }
            intermediateKeyStorage.hasRemoteSecretProtectedInner() -> {
                val remoteSecretProtected = intermediateKeyStorage.remoteSecretProtectedInner!!
                Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                    parameters = RemoteSecretParameters(
                        authenticationToken = RemoteSecretAuthenticationToken(remoteSecretProtected.remoteSecretAuthenticationToken.toByteArray()),
                        remoteSecretHash = RemoteSecretHash(remoteSecretProtected.remoteSecretHash.toByteArray()),
                    ),
                    encryptedData = remoteSecretProtected.encryptedInner.toCryptographicByteArray(),
                )
            }
            else -> error("Invalid intermediate key storage")
        }
    }

    private fun decodeInnerKeyStorage(dataInputStream: DataInputStream): Version2MasterKeyStorageInnerData.Unprotected {
        val innerKeyStorageVersionNumber = dataInputStream.readUnsignedShort()
        when (InnerKeyStorage.Version.forNumber(innerKeyStorageVersionNumber)) {
            InnerKeyStorage.Version.V1_0 -> {
                return decodeInnerKeyStorageV1(dataInputStream)
            }
            else -> error("Unsupported inner version number $innerKeyStorageVersionNumber")
        }
    }

    private fun decodeInnerKeyStorageV1(dataInputStream: DataInputStream): Version2MasterKeyStorageInnerData.Unprotected {
        val innerKeyStorage = InnerKeyStorageV1.parseFrom(dataInputStream)
        return Version2MasterKeyStorageInnerData.Unprotected(
            masterKeyData = MasterKeyData(innerKeyStorage.masterKey.toByteArray()),
        )
    }
}
