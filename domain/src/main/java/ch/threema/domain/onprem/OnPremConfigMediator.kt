package ch.threema.domain.onprem

import ch.threema.domain.protocol.urls.DeviceGroupUrl

data class OnPremConfigMediator(
    val url: DeviceGroupUrl,
    val blob: OnPremConfigBlob,
)
