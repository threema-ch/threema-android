package ch.threema.common

import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.toIntArray() = IntArray(length(), ::getInt)

fun JSONArray.toJSONObjectList(): List<JSONObject> = List(length(), ::getJSONObject)

fun JSONObject.getStringOrNull(name: String): String? =
    if (has(name)) optString(name) else null

/**
 * Decode the json array with the given [name] of the json object to an instance of T.
 *
 * @throws org.json.JSONException if there is no array with the given [name]
 * @throws IllegalArgumentException if the json array is no valid instance of T
 * @throws kotlinx.serialization.SerializationException in case of any decoding-specific error
 */
inline fun <reified T> JSONObject.decodeArray(name: String): T =
    Json.decodeFromString(getJSONArray(name).toString())
