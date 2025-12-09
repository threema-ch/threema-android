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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilePictureFileHandleTest {

    private lateinit var userDataDirectoryMock: File
    private lateinit var legacyUserDataDirectoryMock: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var masterKeyProviderMock: MasterKeyProvider
    private lateinit var profilePictureFileHandleProvider: ProfilePictureFileHandleProvider

    @BeforeTest
    fun setUp() {
        userDataDirectoryMock = createTempDirectory("data")
        legacyUserDataDirectoryMock = createTempDirectory("legacy-data")
        appDirectoryProviderMock = mockk {
            every { userFilesDirectory } returns userDataDirectoryMock
            every { legacyUserFilesDirectory } returns legacyUserDataDirectoryMock
        }
        masterKeyProviderMock = mockk()
        profilePictureFileHandleProvider = ProfilePictureFileHandleProvider(
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
    fun `get user defined profile picture`() {
        val fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(IDENTITY)

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".profile-pictures"),
                        name = ".c-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = File(legacyUserDataDirectoryMock, ".avatar"),
                        name = ".c-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ.nomedia",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `get contact defined profile picture`() {
        val fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(IDENTITY)

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".profile-pictures"),
                        name = ".p-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = File(legacyUserDataDirectoryMock, ".avatar"),
                        name = ".p-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ.nomedia",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `get android defined profile picture`() {
        val fileHandle = profilePictureFileHandleProvider.getAndroidDefinedProfilePicture(IDENTITY)

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".profile-pictures"),
                        name = ".a-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = File(legacyUserDataDirectoryMock, ".avatar"),
                        name = ".a-NXHAH6OFNMTK2J3Y4LG57YQUJ7JCPWKUO64DRS2MGUV2QYOWQGKQ.nomedia",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `delete all`() {
        val profilePictures = File(userDataDirectoryMock, ".profile-pictures")
        profilePictures.mkdir()
        val legacyProfilePictures = File(legacyUserDataDirectoryMock, ".avatar")
        legacyProfilePictures.mkdir()
        val profilePicture = File(profilePictures, "my-avatar.jpg")
        profilePicture.createNewFile()
        val legacyProfilePicture = File(legacyProfilePictures, "my-avatar.jpg")
        legacyProfilePicture.createNewFile()

        profilePictureFileHandleProvider.deleteAll()

        assertTrue(userDataDirectoryMock.exists())
        assertTrue(legacyUserDataDirectoryMock.exists())
        assertTrue(profilePictures.exists())
        assertTrue(legacyProfilePictures.exists())
        assertFalse(profilePicture.exists())
        assertFalse(legacyProfilePicture.exists())
    }

    companion object {
        private const val IDENTITY = "ABCD1234"
    }
}
