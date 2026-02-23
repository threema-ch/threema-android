package ch.threema.domain.protocol.urls

import ch.threema.common.models.Coordinates
import java.net.URLEncoder
import java.util.Locale

class MapPoiNamesUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = arrayOf("latitude", "longitude", "query")) {
    fun get(coordinates: Coordinates, query: String) = getUrl(
        "latitude" to "%f".format(Locale.US, coordinates.latitude),
        "longitude" to "%f".format(Locale.US, coordinates.longitude),
        "query" to URLEncoder.encode(query, "UTF-8").replace("+", "%20"),
    )
}
