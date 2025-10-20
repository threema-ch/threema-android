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
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.MasterKeyTestData.Version1.PROTECTED_KEY
import ch.threema.localcrypto.MasterKeyTestData.Version1.SALT
import ch.threema.localcrypto.MasterKeyTestData.Version1.VERIFICATION
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import ch.threema.testhelpers.loadResourceAsBytes
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Version1MasterKeyFileManagerTest {

    private lateinit var file: File

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("key", ".dat")
    }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `file exists`() {
        assertTrue(Version1MasterKeyFileManager(file).keyFileExists())
        file.delete()
        assertFalse(Version1MasterKeyFileManager(file).keyFileExists())
    }

    @Test
    fun `read unprotected file `() {
        file.writeBytes(loadResourceAsBytes("masterkey/v1/unprotected.dat"))
        val masterKeyFileManager = Version1MasterKeyFileManager(file)

        val masterKeyFileData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version1(
                Version1MasterKeyStorageData.Unprotected(
                    masterKeyData = MasterKeyData(MASTER_KEY),
                    verification = VERIFICATION.toCryptographicByteArray(),
                ),
            ),
            masterKeyFileData,
        )
    }

    @Test
    fun `read file with Scrypt protected passphrase`() {
        file.writeBytes(loadResourceAsBytes("masterkey/v1/passphrase-protected.dat"))
        val masterKeyFileManager = Version1MasterKeyFileManager(file)

        val masterKeyFileData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version1(
                Version1MasterKeyStorageData.PassphraseProtected(
                    protectedKey = PROTECTED_KEY.toCryptographicByteArray(),
                    salt = SALT.toCryptographicByteArray(),
                    verification = VERIFICATION.toCryptographicByteArray(),
                ),
            ),
            masterKeyFileData,
        )
    }
}
