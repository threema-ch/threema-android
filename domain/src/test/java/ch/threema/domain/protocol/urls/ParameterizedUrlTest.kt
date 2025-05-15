/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.domain.protocol.urls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParameterizedUrlTest {
    @Test
    fun `parameterized url equality`() {
        assertEquals(BlobUrl("foo"), BlobUrl("foo"))
        assertNotEquals<ParameterizedUrl>(BlobUrl("foo"), MapPoiAroundUrl("foo"))
    }

    @Test
    fun `placeholder replacement in url template`() {
        val parameterizeUrl = object : ParameterizedUrl(
            template = "https://example.com/{placeholder1}/{placeholder2}/",
            requiredPlaceholders = emptyArray(),
        ) {
            fun get(placeholder1: String, placeholder2: String) =
                getUrl(
                    "placeholder1" to placeholder1,
                    "placeholder2" to placeholder2,
                )
        }

        val url = parameterizeUrl.get(placeholder1 = "foo", placeholder2 = "bar")

        assertEquals("https://example.com/foo/bar/", url)
    }
}
