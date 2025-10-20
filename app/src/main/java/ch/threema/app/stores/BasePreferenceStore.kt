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

package ch.threema.app.stores

import java.time.Instant
import org.json.JSONArray

abstract class BasePreferenceStore : PreferenceStore {
    override fun save(key: String, value: Instant?) {
        save(key, value?.toEpochMilli() ?: 0L)
    }

    override fun getInstant(key: String): Instant? =
        getLong(key)
            .takeUnless { it == 0L }
            ?.let(Instant::ofEpochMilli)

    protected fun Map<String, String?>.encodeToJSONArray(): JSONArray {
        val json = JSONArray()
        for ((key, value) in this) {
            val keyValueArray = JSONArray()
            keyValueArray.put(key)
            keyValueArray.put(value)
            json.put(keyValueArray)
        }
        return json
    }

    protected fun JSONArray.decodeToStringMap(): Map<String, String?> {
        val jsonArray = this
        return buildMap {
            for (n in 0 until jsonArray.length()) {
                val keyValuePair = jsonArray.getJSONArray(n)
                val key = keyValuePair.getString(0)
                val value = if (!keyValuePair.isNull(1)) {
                    keyValuePair.getString(1)
                } else {
                    null
                }
                put(key, value)
            }
        }
    }

    protected fun JSONArray.decodeToIntMap(): Map<Int, String> {
        val jsonArray = this
        return buildMap {
            for (n in 0 until jsonArray.length()) {
                val keyValuePair = jsonArray.getJSONArray(n)
                val key = keyValuePair.getInt(0)
                val value = keyValuePair.getString(1)
                put(key, value)
            }
        }
    }

    protected fun Array<String>.encodeToString(): String {
        require(none { STRING_ARRAY_SEPARATOR in it })
        return joinToString(separator = STRING_ARRAY_SEPARATOR)
    }

    protected fun String.decodeToStringArray(): Array<String> =
        split(STRING_ARRAY_SEPARATOR).dropLastWhile(String::isEmpty).toTypedArray()

    companion object {
        private const val STRING_ARRAY_SEPARATOR = ";"
    }
}
