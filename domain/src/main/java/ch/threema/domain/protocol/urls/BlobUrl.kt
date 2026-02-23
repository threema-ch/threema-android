package ch.threema.domain.protocol.urls

import ch.threema.common.toHexString

class BlobUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = arrayOf("blobId")) {
    /**
     * @throws IllegalArgumentException if the [blobId] is invalid
     */
    fun get(blobId: ByteArray): String {
        require(blobId.isNotEmpty()) {
            "Invalid blobId"
        }
        val blobIdHexString = blobId.toHexString()
        return getUrl(
            "blobId" to blobIdHexString,
            "blobIdPrefix" to blobIdHexString.substring(0, 2),
        )
    }
}
