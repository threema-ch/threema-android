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

import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.AUTH_TOKEN
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET_HASH
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import com.google.protobuf.kotlin.toByteStringUtf8
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Version2MasterKeyCryptoTest {
    @Test
    fun `encrypt with passphrase`() {
        val innerDataMock = mockk<Version2MasterKeyStorageInnerData.Unprotected>()
        val masterKeyCrypto = Version2MasterKeyCrypto(
            decoder = mockk(),
            encoder = mockk {
                every { encodeInnerData(any()) } returns ENCODED_INNER_DATA.toByteStringUtf8()
            },
            random = mockSecureRandom(),
        )

        val outerData = masterKeyCrypto.encryptWithPassphrase(
            innerData = innerDataMock,
            passphrase = PASSPHRASE,
        )

        assertEquals(
            Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = PASSPHRASE_ENCRYPTED_DATA.toCryptographicByteArray(),
                nonce = NONCE.toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
            outerData,
        )
    }

    @Test
    fun `decrypt with passphrase`() {
        val innerDataMock = mockk<Version2MasterKeyStorageInnerData>()
        val masterKeyCrypto = Version2MasterKeyCrypto(
            encoder = mockk(),
            decoder = mockk {
                every { decodeIntermediateKeyStorage(match { String(it.readAllBytes()) == ENCODED_INNER_DATA }) } returns innerDataMock
            },
            random = mockk(),
        )

        val innerData = masterKeyCrypto.decryptWithPassphrase(
            outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = PASSPHRASE_ENCRYPTED_DATA.toCryptographicByteArray(),
                nonce = NONCE.toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
            passphrase = PASSPHRASE,
        )

        assertEquals(innerDataMock, innerData)
    }

    @Test
    fun `decrypt fails with wrong passphrase`() {
        val innerDataMock = mockk<Version2MasterKeyStorageInnerData>()
        val masterKeyCrypto = Version2MasterKeyCrypto(
            encoder = mockk(),
            decoder = mockk {
                every { decodeIntermediateKeyStorage(match { String(it.readAllBytes()) == ENCODED_INNER_DATA }) } returns innerDataMock
            },
            random = mockk(),
        )

        assertFailsWith<CryptoException> {
            masterKeyCrypto.decryptWithPassphrase(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = PASSPHRASE_ENCRYPTED_DATA.toCryptographicByteArray(),
                    nonce = NONCE.toCryptographicByteArray(),
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
                passphrase = "wrong passphrase".toCharArray(),
            )
        }
    }

    @Test
    fun `encrypt with remote secret`() {
        val parametersMock = mockk<RemoteSecretParameters>()
        val masterKeyCrypto = Version2MasterKeyCrypto(
            decoder = mockk(),
            encoder = mockk {
                every {
                    encodeInnerData(Version2MasterKeyStorageInnerData.Unprotected(MASTER_KEY_DATA))
                } returns ENCODED_INNER_DATA.toByteStringUtf8()
            },
            random = mockSecureRandom(),
        )

        val innerData = masterKeyCrypto.encryptWithRemoteSecret(
            remoteSecret = REMOTE_SECRET,
            parameters = parametersMock,
            innerData = Version2MasterKeyStorageInnerData.Unprotected(
                masterKeyData = MASTER_KEY_DATA,
            ),
        )

        assertEquals(
            Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                parameters = parametersMock,
                encryptedData = REMOTE_SECRET_ENCRYPTED_DATA.toCryptographicByteArray(),
            ),
            innerData,
        )
    }

    @Test
    fun `decrypt with remote secret`() {
        val masterKeyCrypto = Version2MasterKeyCrypto(
            encoder = mockk(),
            decoder = mockk {
                every {
                    decodeIntermediateKeyStorage(match { String(it.readAllBytes()) == ENCODED_INNER_DATA })
                } returns Version2MasterKeyStorageInnerData.Unprotected(MASTER_KEY_DATA)
            },
            random = mockk(),
        )

        val innerData = masterKeyCrypto.decryptWithRemoteSecret(
            remoteSecret = REMOTE_SECRET,
            innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                parameters = mockk(),
                encryptedData = REMOTE_SECRET_ENCRYPTED_DATA.toCryptographicByteArray(),
            ),
        )

        assertEquals(
            Version2MasterKeyStorageInnerData.Unprotected(
                masterKeyData = MASTER_KEY_DATA,
            ),
            innerData,
        )
    }

    @Test
    fun `encrypt and decrypt with remote secret`() {
        val masterKeyCrypto = Version2MasterKeyCrypto(
            decoder = Version2MasterKeyStorageDecoder(),
            encoder = Version2MasterKeyStorageEncoder(),
            random = mockSecureRandom(),
        )
        val innerData = Version2MasterKeyStorageInnerData.Unprotected(
            masterKeyData = MASTER_KEY_DATA,
        )
        val protected = masterKeyCrypto.encryptWithRemoteSecret(
            remoteSecret = REMOTE_SECRET,
            parameters = RemoteSecretParameters(
                authenticationToken = AUTH_TOKEN,
                remoteSecretHash = REMOTE_SECRET_HASH,
            ),
            innerData = innerData,
        )
        val unprotected = masterKeyCrypto.decryptWithRemoteSecret(
            remoteSecret = REMOTE_SECRET,
            innerData = protected,
        )

        assertEquals(unprotected, innerData)
    }

    @Test
    fun `decrypt fails with wrong remote secret`() {
        val masterKeyCrypto = Version2MasterKeyCrypto(
            encoder = mockk(),
            decoder = mockk(),
            random = mockk(),
        )

        assertFailsWith<CryptoException> {
            val wrongSecret = REMOTE_SECRET.value.copyOf()
            wrongSecret[0] = 0
            masterKeyCrypto.decryptWithRemoteSecret(
                remoteSecret = RemoteSecret(wrongSecret),
                innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                    parameters = mockk(),
                    encryptedData = REMOTE_SECRET_ENCRYPTED_DATA.toCryptographicByteArray(),
                ),
            )
        }
    }

    companion object {
        private fun mockSecureRandom(): SecureRandom =
            mockk {
                every { nextBytes(any()) } answers {
                    val byteArray = firstArg<ByteArray>()
                    byteArray.indices.forEach { i ->
                        byteArray[i] = i.toByte()
                    }
                }
            }

        private val PASSPHRASE = "passphrase".toCharArray()
        private val PASSPHRASE_ENCRYPTED_DATA = byteArrayOf(
            19, 120, -44, -115, 51, -6, -39, -83, 47, 79, -16,
            17, 45, -126, 124, 120, -77, -108, 64, 33, 71, 111, 95,
        )
        private val REMOTE_SECRET_ENCRYPTED_DATA = byteArrayOf(
            10, 24, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 18, 23, 46, 77, 107,
            -31, 44, 110, 89, 23, 74, -31, 50, 71, -58, 4, 123, 96,
            -36, -85, -41, 45, 68, 107, 93,
        )
        private val REMOTE_SECRET = RemoteSecret("my-remote-secret".toByteArray())
        private val MASTER_KEY_DATA = MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH) { (100 - it).toByte() })
        private val NONCE = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }
        private const val ENCODED_INNER_DATA = "encoded"
    }
}
