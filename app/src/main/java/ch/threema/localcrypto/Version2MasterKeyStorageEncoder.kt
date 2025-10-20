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

import ch.threema.base.utils.toByteString
import ch.threema.common.toU16littleEndian
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.localcrypto.protobuf.InnerKeyStorage
import ch.threema.localcrypto.protobuf.IntermediateKeyStorage
import ch.threema.localcrypto.protobuf.IntermediateKeyStorageV1Kt.remoteSecretProtected
import ch.threema.localcrypto.protobuf.OuterKeyStorage
import ch.threema.localcrypto.protobuf.OuterKeyStorageV1.Argon2idProtected
import ch.threema.localcrypto.protobuf.OuterKeyStorageV1Kt
import ch.threema.localcrypto.protobuf.innerKeyStorageV1
import ch.threema.localcrypto.protobuf.intermediateKeyStorageV1
import ch.threema.localcrypto.protobuf.outerKeyStorageV1
import ch.threema.protobuf.combineEncryptedDataAndNonce
import com.google.protobuf.ByteString
import com.google.protobuf.Internal
import com.google.protobuf.MessageLite
import com.google.protobuf.kotlin.plus
import com.google.protobuf.kotlin.toByteString

class Version2MasterKeyStorageEncoder {
    fun encodeMasterKeyStorageData(masterKeyStorageData: MasterKeyStorageData.Version2): ByteString =
        encodeOuterData(masterKeyStorageData.outerData)

    private fun encodeOuterData(outerData: Version2MasterKeyStorageOuterData): ByteString =
        encodeVersionedMessage(
            version = OuterKeyStorage.Version.V1_0,
            message = outerKeyStorageV1 {
                when (outerData) {
                    is Version2MasterKeyStorageOuterData.PassphraseProtected -> {
                        argon2IdProtectedIntermediate = encodePassphraseProtected(outerData)
                    }

                    is Version2MasterKeyStorageOuterData.NotPassphraseProtected -> {
                        plaintextIntermediate = encodeInnerData(outerData.innerData)
                    }
                }
            },
        )

    private fun encodePassphraseProtected(outerData: Version2MasterKeyStorageOuterData.PassphraseProtected): Argon2idProtected =
        OuterKeyStorageV1Kt.argon2idProtected {
            version = when (outerData.argonVersion) {
                Argon2Version.VERSION_1_3 -> Argon2idProtected.Argon2Version.VERSION_1_3
            }
            iterations = outerData.iterations
            memoryBytes = outerData.memoryBytes
            parallelism = outerData.parallelism
            salt = outerData.salt.toByteString()
            encryptedIntermediate = combineEncryptedDataAndNonce(
                data = outerData.encryptedData.value,
                nonce = outerData.nonce.value,
            ).toByteString()
        }

    fun encodeInnerData(innerData: Version2MasterKeyStorageInnerData): ByteString =
        encodeVersionedMessage(
            version = IntermediateKeyStorage.Version.V1_0,
            message = intermediateKeyStorageV1 {
                when (innerData) {
                    is Version2MasterKeyStorageInnerData.RemoteSecretProtected -> {
                        this.remoteSecretProtectedInner = remoteSecretProtected {
                            remoteSecretHash = innerData.parameters.remoteSecretHash.toByteString()
                            remoteSecretAuthenticationToken = innerData.parameters.authenticationToken.toByteString()
                            encryptedInner = innerData.encryptedData.toByteString()
                        }
                    }
                    is Version2MasterKeyStorageInnerData.Unprotected -> {
                        plaintextInner = encodeVersionedMessage(
                            version = InnerKeyStorage.Version.V1_0,
                            message = innerKeyStorageV1 {
                                masterKey = innerData.masterKeyData.toByteString()
                            },
                        )
                    }
                }
            },
        )

    private fun encodeVersionedMessage(version: Internal.EnumLite, message: MessageLite): ByteString =
        version.toByteString() + message.toByteString()

    private fun Internal.EnumLite.toByteString(): ByteString =
        number.toU16littleEndian().toByteString()
}
