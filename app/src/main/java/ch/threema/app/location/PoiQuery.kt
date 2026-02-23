package ch.threema.app.location

import ch.threema.common.models.Coordinates

data class PoiQuery(val query: String?, val center: Coordinates)
