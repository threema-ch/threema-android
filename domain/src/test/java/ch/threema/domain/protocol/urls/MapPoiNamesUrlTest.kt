package ch.threema.domain.protocol.urls

import ch.threema.common.models.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPoiNamesUrlTest {
    @Test
    fun `placeholders are replaced`() {
        val mapPoiNamesUrl = MapPoiNamesUrl("https://poi.threema.ch/names/{latitude}/{longitude}/{query}/")

        val url = mapPoiNamesUrl.get(coordinates = Coordinates(47.220087, 8.808609), query = "Zürich City")

        assertEquals("https://poi.threema.ch/names/47.220087/8.808609/Z%C3%BCrich%20City/", url)
    }
}
