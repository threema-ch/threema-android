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
import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.MasterKeyTestData.Version1.VERIFICATION
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MasterKeyStorageStateConverterTest {
    @Test
    fun `unprotected version 1 to key state`() {
        val converter = MasterKeyStorageStateConverter()

        val keyState = converter.toKeyState(
            MasterKeyStorageData.Version1(
                data = Version1MasterKeyStorageData.Unprotected(
                    masterKeyData = MasterKeyData(MASTER_KEY),
                    verification = VERIFICATION.toCryptographicByteArray(),
                ),
            ),
        )
        assertEquals(
            MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY),
                wasMigrated = true,
            ),
            keyState,
        )
    }

    @Test
    fun `unprotected version 2 to key state`() {
        val converter = MasterKeyStorageStateConverter()

        val keyState = converter.toKeyState(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
        )
        assertEquals(
            MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY),
                wasMigrated = false,
            ),
            keyState,
        )
    }

    @Test
    fun `passphrase version 1 protected to key state`() {
        val converter = MasterKeyStorageStateConverter()
        val protectedKeyMock = mockk<CryptographicByteArray>()
        val saltMock = mockk<CryptographicByteArray>()
        val verificationMock = mockk<CryptographicByteArray>()

        val keyState = converter.toKeyState(
            MasterKeyStorageData.Version1(
                data = Version1MasterKeyStorageData.PassphraseProtected(
                    protectedKey = protectedKeyMock,
                    salt = saltMock,
                    verification = verificationMock,
                ),
            ),
        )

        assertEquals(
            MasterKeyState.WithPassphrase(
                protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version1(
                    protectedKey = protectedKeyMock,
                    salt = saltMock,
                    verification = verificationMock,
                ),
            ),
            keyState,
        )
    }

    @Test
    fun `passphrase version 2 protected to key state`() {
        val converter = MasterKeyStorageStateConverter()
        val encryptedDataMock = mockk<CryptographicByteArray>()
        val nonceMock = mockk<CryptographicByteArray>()
        val saltMock = mockk<CryptographicByteArray>()

        val keyState = converter.toKeyState(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = encryptedDataMock,
                    nonce = nonceMock,
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = saltMock,
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
        )

        assertEquals(
            MasterKeyState.WithPassphrase(
                protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version2(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = encryptedDataMock,
                    nonce = nonceMock,
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = saltMock,
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
            keyState,
        )
    }

    @Test
    fun `remote secret protected to key state`() {
        val converter = MasterKeyStorageStateConverter()
        val parametersMock = mockk<RemoteSecretParameters>()
        val encryptedDataMock = mockk<CryptographicByteArray>()

        val keyState = converter.toKeyState(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = parametersMock,
                        encryptedData = encryptedDataMock,
                    ),
                ),
            ),
        )

        assertEquals(
            MasterKeyState.WithRemoteSecret(
                parameters = parametersMock,
                encryptedData = encryptedDataMock,
            ),
            keyState,
        )
    }

    @Test
    fun `unprotected version 2 to storage data`() {
        val converter = MasterKeyStorageStateConverter()

        val storageData = converter.toStorageData(
            MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY),
                wasMigrated = false,
            ),
        )
        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `passphrase version 1 protected to storage data`() {
        val converter = MasterKeyStorageStateConverter()
        val protectedKeyMock = mockk<CryptographicByteArray>()
        val saltMock = mockk<CryptographicByteArray>()
        val verificationMock = mockk<CryptographicByteArray>()

        assertFailsWith<IllegalStateException> {
            converter.toStorageData(
                MasterKeyState.WithPassphrase(
                    protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version1(
                        protectedKey = protectedKeyMock,
                        salt = saltMock,
                        verification = verificationMock,
                    ),
                ),
            )
        }
    }

    @Test
    fun `passphrase version 2 protected to storage data`() {
        val converter = MasterKeyStorageStateConverter()
        val encryptedDataMock = mockk<CryptographicByteArray>()
        val nonceMock = mockk<CryptographicByteArray>()
        val saltMock = mockk<CryptographicByteArray>()

        val storageData = converter.toStorageData(
            MasterKeyState.WithPassphrase(
                protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version2(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = encryptedDataMock,
                    nonce = nonceMock,
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = saltMock,
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
        )

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = encryptedDataMock,
                    nonce = nonceMock,
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = saltMock,
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `remote secret protected to storage data`() {
        val converter = MasterKeyStorageStateConverter()
        val parametersMock = mockk<RemoteSecretParameters>()
        val encryptedDataMock = mockk<CryptographicByteArray>()

        val storageData = converter.toStorageData(
            MasterKeyState.WithRemoteSecret(
                parameters = parametersMock,
                encryptedData = encryptedDataMock,
            ),
        )

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = parametersMock,
                        encryptedData = encryptedDataMock,
                    ),
                ),
            ),
            storageData,
        )
    }
}
