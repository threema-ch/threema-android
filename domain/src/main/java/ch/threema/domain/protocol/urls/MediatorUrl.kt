package ch.threema.domain.protocol.urls

class MediatorUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = emptyArray()) {
    init {
        require(template.endsWith("/"))
    }

    /**
     * @throws IllegalArgumentException if the [deviceGroupId] is invalid
     */
    fun get(deviceGroupId: ByteArray): String {
        require(deviceGroupId.isNotEmpty()) {
            "Key deviceGroupId is not in correct form"
        }
        val prefix8 = deviceGroupId[0].toHexString()
        val prefix4 = prefix8.take(1)
        return getUrl(
            "deviceGroupIdPrefix4" to prefix4,
            "deviceGroupIdPrefix8" to prefix8,
        )
    }
}
