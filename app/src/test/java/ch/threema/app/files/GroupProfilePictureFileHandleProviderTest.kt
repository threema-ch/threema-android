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

package ch.threema.app.files

import ch.threema.common.files.FallbackFileHandle
import ch.threema.common.files.SimpleFileHandle
import ch.threema.localcrypto.MasterKeyProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupProfilePictureFileHandleProviderTest {
    private lateinit var userDataDirectoryMock: File
    private lateinit var legacyUserDataDirectoryMock: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var masterKeyProviderMock: MasterKeyProvider
    private lateinit var groupProfilePictureFileHandleProvider: GroupProfilePictureFileHandleProvider

    @BeforeTest
    fun setUp() {
        userDataDirectoryMock = createTempDirectory("data")
        legacyUserDataDirectoryMock = createTempDirectory("legacy-data")
        appDirectoryProviderMock = mockk {
            every { userFilesDirectory } returns userDataDirectoryMock
            every { legacyUserFilesDirectory } returns legacyUserDataDirectoryMock
        }
        masterKeyProviderMock = mockk()
        groupProfilePictureFileHandleProvider = GroupProfilePictureFileHandleProvider(
            appDirectoryProvider = appDirectoryProviderMock,
            masterKeyProvider = masterKeyProviderMock,
        )
    }

    @AfterTest
    fun tearDown() {
        userDataDirectoryMock.deleteRecursively()
        legacyUserDataDirectoryMock.deleteRecursively()
    }

    @Test
    fun `get group profile picture`() {
        val fileHandle = groupProfilePictureFileHandleProvider.get(groupDatabaseId = 42)

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".group-profile-pictures"),
                        name = ".gpp-42",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = File(legacyUserDataDirectoryMock, ".grp-avatar"),
                        name = ".grp-avatar-42",
                    ),
                ),
            ),
            fileHandle,
        )
    }
}
