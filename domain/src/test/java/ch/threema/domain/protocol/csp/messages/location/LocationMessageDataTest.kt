/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.csp.messages.location

import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.testhelpers.willThrow
import kotlin.random.Random.Default.nextBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationMessageDataTest {
    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsEmpty() {
        // arrange
        val messageString = ""
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsBlank() {
        // arrange
        val messageString = "          "
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsTooShort() {
        // arrange
        val shortByteArray: ByteArray = nextBytes(4)
        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = shortByteArray,
                offset = 0,
                length = shortByteArray.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetBelowZero() {
        // arrange
        val locationMessageBytes: ByteArray = nextBytes(10)
        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = -1,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthAndOffsetExceedLength() {
        // arrange
        val locationMessageBytes: ByteArray = nextBytes(10)

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 5,
                length = 6,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsNoNumberValues() {
        // arrange
        val messageString = "New York City"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsFewNumberValues1() {
        // arrange
        val messageString = "40.741895"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsFewNumberValues2() {
        // arrange
        val messageString = "40.741895,"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsFewNumberValues3() {
        // arrange
        val messageString = "40.741895,,"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsFewNumberValues4() {
        // arrange
        val messageString = "40.741895,Text"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenFirstLineContainsTooManyNumberValues() {
        // arrange
        val messageString = "40.741895,-73.989308,0.000000,0.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenLatitudeIsTooSmall() {
        // arrange
        val messageString = "-91.000000,-73.989308,0.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenLatitudeIsTooLarge() {
        // arrange
        val messageString = "91.000000,-73.989308,0.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenLongitudeIsTooSmall() {
        // arrange
        val messageString = "41.000000,-181.000000,0.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenLongitudeIsTooLarge() {
        // arrange
        val messageString = "41.000000,181.000000,0.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        val codeUnderTest = {
            // act
            LocationMessageData.parse(
                data = locationMessageBytes,
                offset = 0,
                length = locationMessageBytes.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldSucceedWithLatLong1() {
        // arrange
        val messageString = "40.741895,-73.989308"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLong2() {
        // arrange
        val messageString = "40.74189,-73.98930"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.74189,
                longitude = -73.98930,
                accuracy = null,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLong3() {
        // arrange
        val messageString = "40,-73"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.0,
                longitude = -73.0,
                accuracy = null,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAndAccuracy() {
        // arrange
        val messageString = "40.741895,-73.989308,1.000000"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = 1.0,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAccuracyAndPoiAddress() {
        // arrange
        val messageString = "40.741895,-73.989308,1.000000\nNew York City, Best Street, 1002"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = 1.0,
                poi = Poi.Unnamed(
                    address = "New York City, Best Street, 1002",
                ),
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAccuracyPoiAddressAndPoiName() {
        // arrange
        val messageString =
            "40.741895,-73.989308,1.000000\nGoogle NYC\nNew York City, Best Street, 1002"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = 1.0,
                poi = Poi.Named(
                    name = "Google NYC",
                    address = "New York City, Best Street, 1002",
                ),
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongPoiAddressAndPoiName() {
        // arrange
        val messageString = "40.741895,-73.989308\nGoogle NYC\nNew York City, Best Street, 1002"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = Poi.Named(
                    name = "Google NYC",
                    address = "New York City, Best Street, 1002",
                ),
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAndPoiAddress() {
        // arrange
        val messageString = "40.741895,-73.989308\nNew York City, Best Street, 1002"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = Poi.Unnamed(
                    address = "New York City, Best Street, 1002",
                ),
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAndPoiAddressBlank() {
        // arrange
        val messageString = "40.741895,-73.989308\n   "
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAndPoiNameBlankButAddressNot() {
        // arrange
        val messageString = "40.741895,-73.989308\n   \nCool City, Cool Street"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = Poi.Unnamed(
                    address = "Cool City, Cool Street",
                ),
            ),
            result,
        )
    }

    @Test
    fun shouldSucceedWithLatLongAndPoiNameAndAddressBlank() {
        // arrange
        val messageString = "40.741895,-73.989308\n   \n     "
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = null,
                poi = null,
            ),
            result,
        )
    }

    @Test
    fun shouldIgnoreAnyLinesAfterTheThird() {
        // arrange
        val messageString =
            "40.741895,-73.989308,1.000000\nGoogle NYC\nNew York City, Best Street, 1002\nIgnored\nIgnored too"
        val locationMessageBytes: ByteArray = messageString.toByteArray()

        // act
        val result = LocationMessageData.parse(
            data = locationMessageBytes,
            offset = 0,
            length = locationMessageBytes.size,
        )

        // assert
        assertEquals(
            LocationMessageData(
                latitude = 40.741895,
                longitude = -73.989308,
                accuracy = 1.0,
                poi = Poi.Named(
                    name = "Google NYC",
                    address = "New York City, Best Street, 1002",
                ),
            ),
            result,
        )
    }
}
