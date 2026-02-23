package ch.threema.domain.onprem

import ch.threema.domain.protocol.urls.BlobUrl

data class OnPremConfigBlob(
    val uploadUrl: String,
    val downloadUrl: BlobUrl,
    val doneUrl: BlobUrl,
)
