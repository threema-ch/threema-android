/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.storage.models.data

import ch.threema.domain.protocol.csp.messages.location.Poi
import org.junit.Test
import kotlin.test.assertEquals

class LocationDataModelTest {

    private val testCasesToString: List<Pair<LocationDataModel, String>> = listOf(
        // from least to most possible data
        LocationDataModel(34.423421, 120.456787, null, null) to "[34.423421,120.456787,null,null,null]",
        LocationDataModel(34.423421, 120.456787, 10.56, null) to "[34.423421,120.456787,10.56,null,null]",
        LocationDataModel(34.423421, 120.456787, 10.56, Poi.Unnamed(address = "Address")) to "[34.423421,120.456787,10.56,\"Address\",null]",
        LocationDataModel(34.423421, 120.456787, 10.56, Poi.Named(name = "Name", address = "Address")) to "[34.423421,120.456787,10.56,\"Address\",\"Name\"]",
        // mixed data presence
        LocationDataModel(34.423421, 120.456787, null, Poi.Unnamed("Address")) to "[34.423421,120.456787,null,\"Address\",null]",
        LocationDataModel(34.423421, 120.456787, null, Poi.Named(name = "Name", address = "Address")) to "[34.423421,120.456787,null,\"Address\",\"Name\"]",
        // negative numbers
        LocationDataModel(-34.423421, -120.456787, null, null) to "[-34.423421,-120.456787,null,null,null]",
        LocationDataModel(-34.423421, -120.456787, -10.56, null) to "[-34.423421,-120.456787,-10.56,null,null]",
        // big numbers
        LocationDataModel(3.49823748927349E19, 1.2084363284698237E20, null, null) to "[3.49823748927349E19,1.2084363284698237E20,null,null,null]",
        LocationDataModel(3.49823748927349E19, 1.2084363284698237E20, 1.2378216376128731E19, null) to "[3.49823748927349E19,1.2084363284698237E20,1.2378216376128731E19,null,null]",
        // line breaks
        LocationDataModel(34.423421, 120.456787, 10.56, Poi.Named(name = "Name", address = "Cool Street\nCool City")) to "[34.423421,120.456787,10.56,\"Cool Street\\nCool City\",\"Name\"]",
        LocationDataModel(34.423421, 120.456787, 10.56, Poi.Named(name = "Cool\nName", address = "Cool Street\nCool City")) to "[34.423421,120.456787,10.56,\"Cool Street\\nCool City\",\"Cool\\nName\"]",
        // blank strings
        LocationDataModel(34.423421, 120.456787, 10.56, Poi.Named(name = "   ", address = "     ")) to "[34.423421,120.456787,10.56,\"     \",\"   \"]",
        )

    @Test
    fun toStringShouldReturnCorrectJsonArray() {
        testCasesToString.forEachIndexed { index, testCase: Pair<LocationDataModel, String> ->
            assertEquals(
                expected = testCase.second,
                actual = testCase.first.toString(),
                message = "Assertion failed for testcase at index $index"
            )
        }
    }

    private val testCasesFromString: List<Pair<String, LocationDataModel>> = listOf(
        // minimal data
        "[10.5, 20.0]" to LocationDataModel(10.5, 20.0, null, null),
        "[10.5, 20.0, null]" to LocationDataModel(10.5, 20.0, null, null),
        "[10.5, 20.0, null, null]" to LocationDataModel(10.5, 20.0, null, null),
        "[10.5, 20.0, null, null, null]" to LocationDataModel(10.5, 20.0, null, null),
        // with accuracy
        "[10.5, 20.0, 30.0]" to LocationDataModel(10.5, 20.0, 30.0, null),
        "[10.5, 20.0, 30.0, null]" to LocationDataModel(10.5, 20.0, 30.0, null),
        "[10.5, 20.0, 30.0, null, null]" to LocationDataModel(10.5, 20.0, 30.0, null),
        "[10, 20, 30, null, null]" to LocationDataModel(10.0, 20.0, 30.0, null),
        // with poiAddress
        "[10.5, 20.0, 30.0, \"Address\"]" to LocationDataModel(10.5, 20.0, 30.0, Poi.Unnamed("Address")),
        "[10.5, 20.0, 30.0, \"Address\", null]" to LocationDataModel(10.5, 20.0, 30.0, Poi.Unnamed("Address")),
        "[10.5, 20.0, null, \"Address\", null]" to LocationDataModel(10.5, 20.0, null, Poi.Unnamed("Address")),
        "[10.5, 20.0, null, \"Address\"]" to LocationDataModel(10.5, 20.0, null, Poi.Unnamed("Address")),
        // with poiName
        "[10.5, 20.0, 30.0, \"Address\", \"Name\"]" to LocationDataModel(10.5, 20.0, 30.0, Poi.Named( "Name", "Address")),
        "[10.5, 20.0, null, \"Address\", \"Name\"]" to LocationDataModel(10.5, 20.0, null, Poi.Named( "Name", "Address")),
        // more values are ignored
        "[10.5, 20.0, 30.0, \"Address\", \"Name\", 50.0, {}, []]" to LocationDataModel(10.5, 20.0, 30.0, Poi.Named( "Name", "Address")),
        // failures
        "[]" to LocationDataModel(0.0, 0.0, null, null),
        "{}" to LocationDataModel(0.0, 0.0, null, null),
        "[10.5]" to LocationDataModel(0.0, 0.0, null, null),
        "[10.5, 20.0, \"Address\"]" to LocationDataModel(0.0, 0.0, null, null),
        "[\"Address\"]" to LocationDataModel(0.0, 0.0, null, null),
        "[10.5, 20.0, 10.0, 10.0]" to LocationDataModel(0.0, 0.0, null, null),
    )

    @Test
    fun fromStringOrDefaultShouldReturnCorrectObject(){
        testCasesFromString.forEachIndexed { index, testCase: Pair<String, LocationDataModel> ->
            assertEquals(
                expected = testCase.second,
                actual = LocationDataModel.fromStringOrDefault(testCase.first),
                message = "Assertion failed for testcase at index $index"
            )
        }
    }
}
