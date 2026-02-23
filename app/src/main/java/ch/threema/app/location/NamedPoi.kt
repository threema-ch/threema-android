package ch.threema.app.location

import ch.threema.common.models.Coordinates

/**
 * A point-of-interest, used in places search
 *
 * @param distance The distance in meters between this POI and the queried location
 */
data class NamedPoi(
    val name: String,
    val coordinates: Coordinates,
    val distance: Int,
    val description: String?,
)
