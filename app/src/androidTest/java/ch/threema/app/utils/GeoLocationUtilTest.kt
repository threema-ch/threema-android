/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.utils

import android.net.Uri
import ch.threema.storage.models.data.LocationDataModel
import org.junit.Assert.*
import org.junit.Test

class GeoLocationUtilTest {

    private fun expectLocationData(expected: LocationDataModel?, uriStr: String) {
        val uri = Uri.parse(uriStr)
        val actual = GeoLocationUtil.getLocationDataFromGeoUri(uri)
        if (expected == null) {
            assertNull(actual)
            return
        }
        assertNotNull(actual)
        assertEquals(expected.latitude, actual?.latitude)
        assertEquals(expected.longitude, actual?.longitude)
        assertEquals(expected.poi, actual?.poi)
        assertEquals(expected.address, actual?.address)
        assertEquals(expected.accuracy, actual?.accuracy)
    }

    @Test
    fun testGetLocationFromUri() {
        val latLong1234 = LocationDataModel(12.0, 34.0, 0, "", "")
        expectLocationData(latLong1234, "geo:12,34;abcd=efg")
        expectLocationData(latLong1234, "geo:12.0,34.00;a=b;c=d")
        expectLocationData(latLong1234, "geo:12.0,34.0?q=12.0,34.0")
        expectLocationData(latLong1234, "geo:1.0,2?q=12.0,34.0")
        expectLocationData(latLong1234, "geo:0,0?q=12,34")
        expectLocationData(latLong1234, "geo:12,34,56")
        expectLocationData(latLong1234, "geo:12,34,56?z=12")
    }

}
