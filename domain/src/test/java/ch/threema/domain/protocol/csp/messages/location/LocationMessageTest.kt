package ch.threema.domain.protocol.csp.messages.location

import kotlin.test.Test
import kotlin.test.assertContentEquals

class LocationMessageTest {
    @Test
    fun getBodyReturnsCorrectWithLatLong1() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = null,
                poi = null,
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.876578,120.461526".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLong2() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = -30.876578,
                longitude = -120.461526,
                accuracy = null,
                poi = null,
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "-30.876578,-120.461526".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongRounded() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.0,
                longitude = 120.46152687654568,
                accuracy = null,
                poi = null,
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.000000,120.461527".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongAccuracy() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = 20.34,
                poi = null,
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.876578,120.461526,20.340000".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongAccuracyRounded() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = 20.3434367,
                poi = null,
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.876578,120.461526,20.343437".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongAccuracyPoiAddress() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = 20.34,
                poi = Poi.Unnamed(
                    address = "Cool Street, Cool City, 50",
                ),
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.876578,120.461526,20.340000\nCool Street, Cool City, 50".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongAccuracyPoiAddressLineBreaks() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = 20.34,
                poi = Poi.Unnamed(
                    address = "Cool Street\nCool City\n50",
                ),
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected = "30.876578,120.461526,20.340000\nCool Street\\nCool City\\n50".toByteArray()
        assertContentEquals(expected, body)
    }

    @Test
    fun getBodyReturnsCorrectWithLatLongAccuracyPoiAddressPoiName() {
        // arrange
        val locationMessage = LocationMessage(
            LocationMessageData(
                latitude = 30.876578,
                longitude = 120.461526,
                accuracy = 20.34,
                poi = Poi.Named(
                    name = "Pizza Place",
                    address = "Cool Street, Cool City, 50",
                ),
            ),
        )

        // act
        val body = locationMessage.body

        // assert
        val expected =
            "30.876578,120.461526,20.340000\nPizza Place\nCool Street, Cool City, 50".toByteArray()
        assertContentEquals(expected, body)
    }
}
