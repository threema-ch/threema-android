/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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
