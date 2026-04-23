package ch.threema.domain.protocol.connection.data

import ch.threema.base.utils.generateRandomProtobufPadding

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

            fun fromProtobuf(deviceInfo: ch.threema.protobuf.d2d.DeviceInfo): DeviceInfo {
                val platform: Platform = when (deviceInfo.platform) {
                    ch.threema.protobuf.d2d.DeviceInfo.Platform.UNSPECIFIED -> Platform.UNSPECIFIED
                    ch.threema.protobuf.d2d.DeviceInfo.Platform.ANDROID -> Platform.ANDROID
                    ch.threema.protobuf.d2d.DeviceInfo.Platform.IOS -> Platform.IOS
                    ch.threema.protobuf.d2d.DeviceInfo.Platform.DESKTOP -> Platform.DESKTOP
                    ch.threema.protobuf.d2d.DeviceInfo.Platform.WEB -> Platform.WEB
                    null, ch.threema.protobuf.d2d.DeviceInfo.Platform.UNRECOGNIZED -> Platform.UNSPECIFIED
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
            get() = ch.threema.protobuf.d2d.DeviceInfo.newBuilder()
                .setPadding(generateRandomProtobufPadding())
                .setPlatformValue(platform.value)
                .setPlatformDetails(platformDetails)
                .setAppVersion(appVersion)
                .setLabel(label)
                .build()
                .toByteArray()
    }
}
