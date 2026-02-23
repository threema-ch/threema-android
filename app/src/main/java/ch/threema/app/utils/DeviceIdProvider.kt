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
