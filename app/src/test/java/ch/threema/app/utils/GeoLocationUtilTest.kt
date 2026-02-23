package ch.threema.app.utils

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeoLocationUtilTest {
    @Test
    fun testGetLocationFromUri() {
        expectLocationData("geo:12,34;abcd=efg")
        expectLocationData("geo:12.0,34.00;a=b;c=d")
        expectLocationData("geo:12.0,34.0?q=12.0,34.0")
        expectLocationData("geo:1.0,2?q=12.0,34.0")
        expectLocationData("geo:0,0?q=12,34")
        expectLocationData("geo:12,34,56")
        expectLocationData("geo:12,34,56?z=12")
    }

    private fun expectLocationData(uriStr: String) {
        val uriMock = mockk<Uri>()
        every { uriMock.toString() } returns uriStr

        val locationDataModel = GeoLocationUtil.getLocationDataFromGeoUri(uriMock)
        assertNotNull(locationDataModel)
        assertEquals(12.0, locationDataModel.latitude)
        assertEquals(34.0, locationDataModel.longitude)
    }
}
