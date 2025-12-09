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

import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasterKeyStorageManagerTest {
    @Test
    fun `no key exists`() {
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = mockk {
                every { keyFileExists() } returns false
            },
            version1KeyFileManager = mockk {
                every { keyFileExists() } returns false
            },
            storageStateConverter = mockk(),
        )

        assertFalse(keyStorageManager.keyExists())
    }

    @Test
    fun `reading from existing version 2 key file`() {
        val keyStorageDataMock = mockk<MasterKeyStorageData>()
        val keyStateMock = mockk<MasterKeyState>()
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = mockk {
                every { keyFileExists() } returns true
                every { readKeyFile() } returns keyStorageDataMock
            },
            version1KeyFileManager = mockk(),
            storageStateConverter = mockk {
                every { toKeyState(keyStorageDataMock) } returns keyStateMock
            },
        )

        assertTrue(keyStorageManager.keyExists())
        assertEquals(keyStateMock, keyStorageManager.readKey())
    }

    @Test
    fun `reading from existing version 1 key file`() {
        val keyStorageDataMock = mockk<MasterKeyStorageData.Version1>()
        val keyStateMock = mockk<MasterKeyState>()
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = mockk {
                every { keyFileExists() } returns false
            },
            version1KeyFileManager = mockk {
                every { keyFileExists() } returns true
                every { readKeyFile() } returns keyStorageDataMock
            },
            storageStateConverter = mockk {
                every { toKeyState(keyStorageDataMock) } returns keyStateMock
            },
        )

        assertTrue(keyStorageManager.keyExists())
        assertEquals(keyStateMock, keyStorageManager.readKey())
    }

    @Test
    fun `writing key when version 1 key does not exist`() {
        val version2KeyFileManagerMock = mockk<Version2MasterKeyFileManager>(relaxed = true)
        val version1KeyFileManagerMock = mockk<Version1MasterKeyFileManager>(relaxed = true) {
            every { keyFileExists() } returns false
        }
        val keyStateMock = mockk<MasterKeyState>()
        val storageDataMock = mockk<MasterKeyStorageData.Version2>()
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = version2KeyFileManagerMock,
            version1KeyFileManager = version1KeyFileManagerMock,
            storageStateConverter = mockk {
                every { toStorageData(keyStateMock) } returns storageDataMock
            },
        )

        keyStorageManager.writeKey(keyStateMock)

        verify(exactly = 1) { version2KeyFileManagerMock.writeKeyFile(storageDataMock) }
        verify(exactly = 1) { version1KeyFileManagerMock.deleteFile() }
    }

    @Test
    fun `writing key when version 1 key exists`() {
        val version2KeyFileManagerMock = mockk<Version2MasterKeyFileManager>(relaxed = true)
        val version1KeyFileManagerMock = mockk<Version1MasterKeyFileManager>(relaxed = true) {
            every { keyFileExists() } returns true
            every { readKeyFile() } returns MasterKeyStorageData.Version1(
                data = Version1MasterKeyStorageData.Unprotected(
                    masterKeyData = MasterKeyData(
                        value = MASTER_KEY,
                    ),
                    verification = mockk(),
                ),
            )
        }
        val keyStateMock = mockk<MasterKeyState.Plain> {
            every { masterKeyData } returns MasterKeyData(
                value = MASTER_KEY.copyOf(),
            )
        }
        val storageDataMock = mockk<MasterKeyStorageData.Version2>()
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = version2KeyFileManagerMock,
            version1KeyFileManager = version1KeyFileManagerMock,
            storageStateConverter = mockk {
                every { toStorageData(keyStateMock as MasterKeyState) } returns storageDataMock
            },
        )

        keyStorageManager.writeKey(keyStateMock)

        verify(exactly = 1) { version2KeyFileManagerMock.writeKeyFile(storageDataMock) }
        verify(exactly = 1) { version1KeyFileManagerMock.deleteFile() }
    }

    @Test
    fun `writing key fails when version 1 key exists but has different value`() {
        val version2KeyFileManagerMock = mockk<Version2MasterKeyFileManager>(relaxed = true)
        val version1KeyFileManagerMock = mockk<Version1MasterKeyFileManager>(relaxed = true) {
            every { keyFileExists() } returns true
            every { readKeyFile() } returns MasterKeyStorageData.Version1(
                data = Version1MasterKeyStorageData.Unprotected(
                    masterKeyData = MasterKeyData(
                        value = MASTER_KEY,
                    ),
                    verification = mockk(),
                ),
            )
        }
        val corruptedKey = MASTER_KEY.copyOf()
        corruptedKey[0]++
        val keyStateMock = mockk<MasterKeyState.Plain> {
            every { masterKeyData } returns MasterKeyData(
                value = corruptedKey,
            )
        }
        val storageDataMock = mockk<MasterKeyStorageData.Version2>()
        val keyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = version2KeyFileManagerMock,
            version1KeyFileManager = version1KeyFileManagerMock,
            storageStateConverter = mockk {
                every { toStorageData(keyStateMock as MasterKeyState) } returns storageDataMock
            },
        )

        assertFails {
            keyStorageManager.writeKey(keyStateMock)
        }

        verify(exactly = 0) { version2KeyFileManagerMock.writeKeyFile(storageDataMock) }
        verify(exactly = 0) { version1KeyFileManagerMock.deleteFile() }
    }
}
