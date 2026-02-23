package ch.threema.app.location

import ch.threema.common.models.Coordinates

/**
 * A point-of-interest, for displaying pins on the map
 */
data class NearbyPoi(
    val id: Long,
    val name: String,
    val coordinates: Coordinates,
    val type: String,
    val isNatural: Boolean,
)
