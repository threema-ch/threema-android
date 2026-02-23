package ch.threema.domain.onprem

import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl

data class OnPremConfigMaps(
    val styleUrl: String,
    val poiNamesUrl: MapPoiNamesUrl,
    val poiAroundUrl: MapPoiAroundUrl,
)
