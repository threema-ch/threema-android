package ch.threema.domain.protocol.urls

import ch.threema.common.models.Coordinates
import java.util.Locale

class MapPoiAroundUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = arrayOf("latitude", "longitude", "radius")) {
    fun get(coordinates: Coordinates, radius: Int) = getUrl(
        "latitude" to "%f".format(Locale.US, coordinates.latitude),
        "longitude" to "%f".format(Locale.US, coordinates.longitude),
        "radius" to radius.toString(),
    )
}
