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
