package ch.threema.domain.protocol.blob

/**
 *  This scope only takes effect when dealing with the blob mirror server.
 *  It needs to be passed as a query parameter to all 3 endpoints (upload, download, done).
 */
sealed class BlobScope(
    @JvmField val name: String,
) {
    /**
     *  Blob is **only** present blob mirror server to share between devices in the same device group
     */
    data object Local : BlobScope("local")

    /**
     *  Blob is **both** present on the mirror server and on the default blob server
     */
    data object Public : BlobScope("public")
}
