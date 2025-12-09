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

class WallpaperFileHandleProviderTest {

    private lateinit var userDataDirectoryMock: File
    private lateinit var legacyUserDataDirectoryMock: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var masterKeyProviderMock: MasterKeyProvider
    private lateinit var wallpaperFileHandleProvider: WallpaperFileHandleProvider

    @BeforeTest
    fun setUp() {
        userDataDirectoryMock = createTempDirectory("data")
        legacyUserDataDirectoryMock = createTempDirectory("legacy-data")
        appDirectoryProviderMock = mockk {
            every { userFilesDirectory } returns userDataDirectoryMock
            every { legacyUserFilesDirectory } returns legacyUserDataDirectoryMock
        }
        masterKeyProviderMock = mockk()
        wallpaperFileHandleProvider = WallpaperFileHandleProvider(
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
    fun `get global wallpaper`() {
        val fileHandle = wallpaperFileHandleProvider.getGlobal()

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = userDataDirectoryMock,
                        name = "wallpaper.jpg",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = legacyUserDataDirectoryMock,
                        name = "wallpaper.jpg",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `get chat wallpaper`() {
        val fileHandle = wallpaperFileHandleProvider.get(uniqueId = "ABCD1234")

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".wallpapers"),
                        name = ".w-ABCD1234",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = File(legacyUserDataDirectoryMock, ".wallpaper"),
                        name = ".w-ABCD1234.nomedia",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `delete all`() {
        val global = File(userDataDirectoryMock, "wallpaper.jpg")
        global.createNewFile()
        val legacyGlobal = File(legacyUserDataDirectoryMock, "wallpaper.jpg")
        legacyGlobal.createNewFile()
        val wallpapers = File(userDataDirectoryMock, ".wallpapers")
        wallpapers.mkdir()
        val legacyWallpapers = File(legacyUserDataDirectoryMock, ".wallpaper")
        legacyWallpapers.mkdir()
        val wallpaper = File(wallpapers, "my-wallpaper.jpg")
        wallpaper.createNewFile()
        val legacyWallpaper = File(legacyWallpapers, "my-wallpaper.jpg")
        legacyWallpaper.createNewFile()

        wallpaperFileHandleProvider.deleteAll()

        assertTrue(userDataDirectoryMock.exists())
        assertTrue(legacyUserDataDirectoryMock.exists())
        assertTrue(wallpapers.exists())
        assertTrue(legacyWallpapers.exists())
        assertFalse(global.exists())
        assertFalse(legacyGlobal.exists())
        assertFalse(wallpaper.exists())
        assertFalse(legacyWallpaper.exists())
    }
}
