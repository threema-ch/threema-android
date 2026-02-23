package ch.threema.app.location

import ch.threema.common.models.Coordinates
import ch.threema.storage.models.data.LocationDataModel
import org.maplibre.android.geometry.LatLng

fun LatLng.toCoordinates() =
    Coordinates(latitude = latitude, longitude = longitude)

fun Coordinates.toLatLng() =
    LatLng(latitude = latitude, longitude = longitude)

fun LocationDataModel.toCoordinates() =
    Coordinates(latitude = latitude, longitude = longitude)
