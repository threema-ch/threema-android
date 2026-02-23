package ch.threema.domain.protocol.urls

import ch.threema.base.utils.Utils

class DeviceGroupUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = emptyArray()) {
    /**
     * @throws IllegalArgumentException if the [deviceGroupId] is invalid
     */
    fun get(deviceGroupId: ByteArray): String {
        require(deviceGroupId.isNotEmpty()) {
            "Key deviceGroupId is not in correct form"
        }
        val prefix8 = Utils.byteToHex(deviceGroupId[0], false, false)
        val prefix4 = prefix8.take(1)
        return getUrl(
            "deviceGroupIdPrefix4" to prefix4,
            "deviceGroupIdPrefix8" to prefix8,
        )
    }
}
