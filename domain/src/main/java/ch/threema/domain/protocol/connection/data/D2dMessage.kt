package ch.threema.domain.protocol.connection.data

import ch.threema.base.utils.generateRandomProtobufPadding
import ch.threema.protobuf.d2d.MdD2D

sealed interface D2dMessage {
    val bytes: ByteArray

    data class DeviceInfo(
        val platform: Platform,
        val platformDetails: String,
        val appVersion: String,
        val label: String,
    ) : D2dMessage {
        companion object {
            val INVALID_DEVICE_INFO = DeviceInfo(Platform.UNSPECIFIED, "", "", "")

            fun fromProtobuf(deviceInfo: MdD2D.DeviceInfo): DeviceInfo {
                val platform: Platform = when (deviceInfo.platform) {
                    MdD2D.DeviceInfo.Platform.UNSPECIFIED -> Platform.UNSPECIFIED
                    MdD2D.DeviceInfo.Platform.ANDROID -> Platform.ANDROID
                    MdD2D.DeviceInfo.Platform.IOS -> Platform.IOS
                    MdD2D.DeviceInfo.Platform.DESKTOP -> Platform.DESKTOP
                    MdD2D.DeviceInfo.Platform.WEB -> Platform.WEB
                    null, MdD2D.DeviceInfo.Platform.UNRECOGNIZED -> Platform.UNSPECIFIED
                }
                return DeviceInfo(
                    platform,
                    deviceInfo.platformDetails,
                    deviceInfo.appVersion,
                    deviceInfo.label,
                )
            }
        }

        enum class Platform(val value: Int) {
            UNSPECIFIED(0),
            ANDROID(1),
            IOS(2),
            DESKTOP(3),
            WEB(4),
        }

        override val bytes: ByteArray
            get() = MdD2D.DeviceInfo.newBuilder()
                .setPadding(generateRandomProtobufPadding())
                .setPlatformValue(platform.value)
                .setPlatformDetails(platformDetails)
                .setAppVersion(appVersion)
                .setLabel(label)
                .build()
                .toByteArray()
    }
}
