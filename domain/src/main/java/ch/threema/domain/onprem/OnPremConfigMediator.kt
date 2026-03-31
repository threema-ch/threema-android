package ch.threema.domain.onprem

import ch.threema.domain.protocol.urls.MediatorUrl

data class OnPremConfigMediator(
    val url: MediatorUrl,
    val blob: OnPremConfigBlob,
)
