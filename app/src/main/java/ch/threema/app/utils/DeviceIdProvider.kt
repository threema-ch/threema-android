/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import java.io.IOException
import java.util.UUID
import org.slf4j.Logger

private val logger: Logger = getThreemaLogger("DeviceIdProvider")

class DeviceIdProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
) {
    fun getDeviceId(): String {
        var deviceId: String? = null

        val deviceIdFile = File(appDirectoryProvider.appDataDirectory, DEVICE_ID_FILENAME)
        if (deviceIdFile.exists()) {
            try {
                deviceId = deviceIdFile.readText()
            } catch (e: IOException) {
                logger.error("Failed to read device id", e)
            }
        }

        if (deviceId == null) {
            deviceId = generateDeviceId()
            try {
                deviceIdFile.writeText(deviceId)
            } catch (e: IOException) {
                logger.error("Failed to write device id", e)
            }
        }

        return deviceId
    }

    private fun generateDeviceId() =
        UUID.randomUUID().toString()

    companion object {
        private const val DEVICE_ID_FILENAME = "device_id"
    }
}
