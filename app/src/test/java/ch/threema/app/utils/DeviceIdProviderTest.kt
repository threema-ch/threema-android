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

package ch.threema.app.utils

import ch.threema.app.files.AppDirectoryProvider
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

class DeviceIdProviderTest {

    private lateinit var appDirectory: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var deviceIdProvider: DeviceIdProvider

    @BeforeTest
    fun setUp() {
        appDirectory = createTempDirectory()
        appDirectoryProviderMock = mockk {
            every { appDataDirectory } returns appDirectory
        }
        deviceIdProvider = DeviceIdProvider(
            appDirectoryProvider = appDirectoryProviderMock,
        )
    }

    @AfterTest
    fun tearDown() {
        appDirectory.deleteRecursively()
    }

    @Test
    fun `new device id is generated when none exists`() {
        val deviceIdFile = File(appDirectory, "device_id")
        assertFalse(deviceIdFile.exists())

        val deviceId = deviceIdProvider.getDeviceId()

        assertTrue(deviceId.isNotEmpty())
        assertEquals(deviceIdFile.readText(), deviceId)
    }

    @Test
    fun `device id is read from file`() {
        val deviceIdFile = File(appDirectory, "device_id")
        deviceIdFile.writeText("my-device-id")

        val deviceId = deviceIdProvider.getDeviceId()

        assertEquals("my-device-id", deviceId)
        assertEquals(deviceIdFile.readText(), deviceId)
    }
}
