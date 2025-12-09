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
