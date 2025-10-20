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

import ch.threema.base.utils.generateRandomBytes
import ch.threema.common.emptyByteArray
import ch.threema.common.toCryptographicByteArray
import ch.threema.libthreema.Argon2idParameters
import ch.threema.libthreema.CryptoException as LibthreemaCryptoException
import ch.threema.libthreema.argon2id
import ch.threema.libthreema.blake2bMac256
import ch.threema.libthreema.xchacha20Poly1305Decrypt
import ch.threema.libthreema.xchacha20Poly1305Encrypt
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.protobuf.combineEncryptedDataAndNonce
import ch.threema.protobuf.separateEncryptedDataAndNonce
import java.io.DataInputStream
import java.security.SecureRandom

class Version2MasterKeyCrypto(
    private val encoder: Version2MasterKeyStorageEncoder = Version2MasterKeyStorageEncoder(),
    private val decoder: Version2MasterKeyStorageDecoder = Version2MasterKeyStorageDecoder(),
    private val random: SecureRandom = SecureRandom(),
) {
    @Throws(CryptoException::class)
    fun encryptWithPassphrase(
        innerData: Version2MasterKeyStorageInnerData,
        passphrase: CharArray,
    ): Version2MasterKeyStorageOuterData.PassphraseProtected {
        try {
            val salt = random.generateRandomBytes(MasterKeyConfig.ARGON2_SALT_LENGTH)
            val iterations = MasterKeyConfig.ARGON2_ITERATIONS
            val parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION
            val memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES

            val nonce = generateNonce()
            val secretKey = argon2id(
                password = String(passphrase).toByteArray(),
                salt = salt,
                parameters = Argon2idParameters(
                    memoryCost = memoryBytes.toUInt(),
                    timeCost = iterations.toUInt(),
                    parallelism = parallelism.toUInt(),
                    outputLength = MasterKeyConfig.SECRET_KEY_LENGTH.toUByte(),
                ),
            )
            val data = xchacha20Poly1305Encrypt(
                key = secretKey,
                nonce = nonce,
                data = encoder.encodeInnerData(innerData).toByteArray(),
                associatedData = emptyByteArray(),
            )

            return Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = data.toCryptographicByteArray(),
                nonce = nonce.toCryptographicByteArray(),
                memoryBytes = memoryBytes,
                salt = salt.toCryptographicByteArray(),
                iterations = iterations,
                parallelism = parallelism,
            )
        } catch (e: LibthreemaCryptoException) {
            throw CryptoException(e)
        }
    }

    @Throws(CryptoException::class)
    fun decryptWithPassphrase(
        outerData: Version2MasterKeyStorageOuterData.PassphraseProtected,
        passphrase: CharArray,
    ): Version2MasterKeyStorageInnerData {
        try {
            val secretKey = argon2id(
                password = String(passphrase).toByteArray(),
                salt = outerData.salt.value,
                parameters = Argon2idParameters(
                    memoryCost = outerData.memoryBytes.toUInt(),
                    timeCost = outerData.iterations.toUInt(),
                    parallelism = outerData.parallelism.toUInt(),
                    outputLength = MasterKeyConfig.SECRET_KEY_LENGTH.toUByte(),
                ),
            )
            val innerData = xchacha20Poly1305Decrypt(
                key = secretKey,
                nonce = outerData.nonce.value,
                data = outerData.encryptedData.value,
                associatedData = emptyByteArray(),
            )

            return decoder.decodeIntermediateKeyStorage(DataInputStream(innerData.inputStream()))
        } catch (e: LibthreemaCryptoException) {
            throw CryptoException(e)
        }
    }

    @Throws(CryptoException::class)
    fun encryptWithRemoteSecret(
        remoteSecret: RemoteSecret,
        parameters: RemoteSecretParameters,
        innerData: Version2MasterKeyStorageInnerData.Unprotected,
    ): Version2MasterKeyStorageInnerData.RemoteSecretProtected {
        try {
            val nonce = generateNonce()
            val secretKey = deriveSecretKey(remoteSecret)
            return Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                parameters = parameters,
                encryptedData = combineEncryptedDataAndNonce(
                    data = xchacha20Poly1305Encrypt(
                        key = secretKey,
                        nonce = nonce,
                        data = encoder.encodeInnerData(innerData).toByteArray(),
                        associatedData = emptyByteArray(),
                    ),
                    nonce = nonce,
                )
                    .toCryptographicByteArray(),
            )
        } catch (e: LibthreemaCryptoException) {
            throw CryptoException(e)
        }
    }

    @Throws(CryptoException::class)
    fun decryptWithRemoteSecret(
        remoteSecret: RemoteSecret,
        innerData: Version2MasterKeyStorageInnerData.RemoteSecretProtected,
    ): Version2MasterKeyStorageInnerData.Unprotected {
        try {
            val (encryptedData, nonce) = separateEncryptedDataAndNonce(innerData.encryptedData.value)
            val secretKey = deriveSecretKey(remoteSecret)

            val innerKeyStorageData = xchacha20Poly1305Decrypt(
                key = secretKey,
                nonce = nonce,
                data = encryptedData,
                associatedData = emptyByteArray(),
            )

            val innerData = decoder.decodeIntermediateKeyStorage(DataInputStream(innerKeyStorageData.inputStream()))
            if (innerData !is Version2MasterKeyStorageInnerData.Unprotected) {
                error("Inner storage data is of wrong type")
            }
            return innerData
        } catch (e: LibthreemaCryptoException) {
            throw CryptoException(e)
        }
    }

    private fun generateNonce() =
        random.generateRandomBytes(MasterKeyConfig.NONCE_LENGTH)

    private fun deriveSecretKey(remoteSecret: RemoteSecret) =
        blake2bMac256(
            key = remoteSecret.value,
            personal = "3ma-rs".toByteArray(),
            salt = "rssk-a".toByteArray(),
            data = emptyByteArray(),
        )
}
