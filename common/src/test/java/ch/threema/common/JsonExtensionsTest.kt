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

package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class JsonExtensionsTest {
    @Test
    fun `JSONArray to IntArray`() {
        val jsonArray = JSONArray("[1, 3, 42]")

        assertContentEquals(
            intArrayOf(1, 3, 42),
            jsonArray.toIntArray(),
        )
    }

    @Test
    fun `JSONArray to IntArray, with invalid data`() {
        val jsonArray = JSONArray("[1, 3, \"not a number\"]")

        assertFailsWith<JSONException> {
            jsonArray.toIntArray()
        }
    }

    @Test
    fun `JSONArray to list of JSONObject`() {
        val jsonArray = JSONArray("""[{}, {"key": "value"}]""")

        assertContentEquals(
            listOf(
                JSONObject().toString(),
                JSONObject("""{"key": "value"}""").toString(),
            ),
            jsonArray.toJSONObjectList().map { it.toString() },
        )
    }

    @Test
    fun `JSONArray to list of JSONObject, with invalid data`() {
        val jsonArray = JSONArray("""[{}, 42]""")

        assertFailsWith<JSONException> {
            jsonArray.toJSONObjectList()
        }
    }

    @Test
    fun `get string or null`() {
        val jsonObject = JSONObject()
        jsonObject.put("A", "Hello")

        assertEquals("Hello", jsonObject.getStringOrNull("A"))
        assertNull(jsonObject.getStringOrNull("B"))
    }
}
