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

import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyStorageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val keyStorageDataMock = mockk<MasterKeyStorageData>()
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
    fun `writing key`() {
        val version2KeyFileManagerMock = mockk<Version2MasterKeyFileManager>(relaxed = true)
        val version1KeyFileManagerMock = mockk<Version1MasterKeyFileManager>(relaxed = true)
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
}
