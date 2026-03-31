package ch.threema.domain.protocol.urls

import ch.threema.common.emptyByteArray
import ch.threema.domain.protocol.csp.ProtocolDefines.BLOB_ID_LEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobUrlTest {
    @Test
    fun `blobId placeholders are replaced`() {
        val blobUrl = BlobUrl("https://ds-blobp-{blobIdPrefix}.test.threema.ch/download/{blobId}/")

        val url = blobUrl.get(blobId = BLOB_ID)

        assertEquals("https://ds-blobp-f0.test.threema.ch/download/f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff/", url)
    }

    @Test
    fun `empty blobId is considered invalid`() {
        val blobUrl = BlobUrl("https://ds-blobp-{blobIdPrefix}.test.threema.ch/download/{blobId}/")

        assertFailsWith<IllegalArgumentException> {
            blobUrl.get(blobId = emptyByteArray())
        }
    }

    companion object {
        private val BLOB_ID = ByteArray(BLOB_ID_LEN) { (it + 0xF0).toByte() }
    }
}
