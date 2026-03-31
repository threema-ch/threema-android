package ch.threema.domain.protocol.urls

class BlobMirrorUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = emptyArray()) {
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
