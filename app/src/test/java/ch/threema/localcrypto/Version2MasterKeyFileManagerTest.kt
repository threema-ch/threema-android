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
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET_HASH
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.testhelpers.cryptographicByteArrayOf
import ch.threema.testhelpers.loadResourceAsBytes
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Version2MasterKeyFileManagerTest {

    private lateinit var file: File
    private lateinit var masterKeyFileManager: Version2MasterKeyFileManager

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("key", ".dat")
        masterKeyFileManager = Version2MasterKeyFileManager(file)
    }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `read unprotected key file`() {
        file.writeBytes(loadResourceAsBytes("masterkey/v2/unprotected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

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
    fun `write unprotected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/unprotected.dat"),
            file.readBytes(),
        )
    }

    @Test
    fun `write and read unprotected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.Unprotected(
                    masterKeyData = MasterKeyData(MASTER_KEY),
                ),
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(writtenKey)
        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }

    @Test
    fun `read passphrase protected key file`() {
        file.writeBytes(loadResourceAsBytes("masterkey/v2/passphrase-protected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = (ByteArray(0) + ByteArray(100) { it.toByte() }).toCryptographicByteArray(),
                    nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `write passphrase protected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = (ByteArray(0) + ByteArray(100) { it.toByte() }).toCryptographicByteArray(),
                    nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/passphrase-protected.dat"),
            file.readBytes(),
        )
    }

    @Test
    fun `write and read passphrase protected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = (ByteArray(0) + ByteArray(100) { it.toByte() }).toCryptographicByteArray(),
                nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(writtenKey)

        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }

    @Test
    fun `read remote secret protected key file`() {
        file.writeBytes(loadResourceAsBytes("masterkey/v2/remote-secret-protected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = RemoteSecretParameters(
                            authenticationToken = AUTH_TOKEN,
                            remoteSecretHash = REMOTE_SECRET_HASH,
                        ),
                        encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                    ),
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `write remote secret protected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = RemoteSecretParameters(
                            authenticationToken = AUTH_TOKEN,
                            remoteSecretHash = REMOTE_SECRET_HASH,
                        ),
                        encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                    ),
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/remote-secret-protected.dat"),
            file.readBytes(),
        )
    }

    @Test
    fun `write and read remote secret protected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                    parameters = RemoteSecretParameters(
                        authenticationToken = AUTH_TOKEN,
                        remoteSecretHash = REMOTE_SECRET_HASH,
                    ),
                    encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                ),
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManager(file)

        masterKeyFileManager.writeKeyFile(writtenKey)

        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }
}
