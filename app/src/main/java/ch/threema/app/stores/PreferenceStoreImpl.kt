/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.content.SharedPreferences
import androidx.core.content.edit
import ch.threema.base.utils.Utils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toHexString
import org.json.JSONArray
import org.json.JSONObject

private val logger = getThreemaLogger("PreferenceStoreImpl")

class PreferenceStoreImpl(
    private val sharedPreferences: SharedPreferences,
    private val onChanged: (key: String, value: Any?) -> Unit,
) : BasePreferenceStore() {

    override fun remove(key: String) {
        sharedPreferences.edit {
            remove(key)
        }
    }

    override fun remove(keys: Set<String>) {
        sharedPreferences.edit {
            for (key in keys) {
                remove(key)
            }
        }
    }

    override fun save(key: String, value: String?) {
        sharedPreferences.edit {
            putString(key, value)
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: Map<String, String?>) {
        val json = value.encodeToJSONArray()
        save(key, json)
    }

    override fun save(key: String, value: Array<String>) {
        saveQuietly(key, value)
        onChanged(key, value)
    }

    override fun saveQuietly(key: String, value: Array<String>) {
        sharedPreferences.edit {
            putString(key, value.encodeToString())
        }
    }

    override fun save(key: String, value: Long) {
        sharedPreferences.edit {
            putLong(key, value)
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: Int) {
        sharedPreferences.edit {
            putInt(key, value)
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: Boolean) {
        sharedPreferences.edit {
            putBoolean(key, value)
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: ByteArray) {
        sharedPreferences.edit {
            putString(key, value.toHexString())
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONArray) {
        sharedPreferences.edit {
            putString(key, value.toString())
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: Float) {
        sharedPreferences.edit {
            putFloat(key, value)
        }
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONObject) {
        sharedPreferences.edit {
            putString(key, value.toString())
        }
        onChanged(key, value)
    }

    override fun getString(key: String): String? =
        try {
            sharedPreferences.getString(key, null)
        } catch (e: ClassCastException) {
            logger.error("Class cast exception", e)
            null
        }

    override fun getStringArray(key: String): Array<String>? =
        sharedPreferences.getString(key, null)
            ?.takeUnlessEmpty()
            ?.decodeToStringArray()

    override fun getMap(key: String): Map<String, String?> =
        try {
            val jsonArray = JSONArray(sharedPreferences.getString(key, "[]"))
            jsonArray.decodeToStringMap()
        } catch (e: Exception) {
            logger.error("Failed to decode string map", e)
            emptyMap()
        }

    @Deprecated("only kept for system update, use getMap instead")
    override fun getIntMap(key: String): Map<Int, String> =
        try {
            val jsonArray = JSONArray(sharedPreferences.getString(key, "[]"))
            jsonArray.decodeToIntMap()
        } catch (e: Exception) {
            logger.error("Failed to decode stored int map", e)
            emptyMap()
        }

    override fun getLong(key: String, defaultValue: Long): Long =
        sharedPreferences.getLong(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int =
        sharedPreferences.getInt(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        sharedPreferences.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        sharedPreferences.getBoolean(key, defaultValue)

    override fun getBytes(key: String): ByteArray =
        sharedPreferences.getString(key, null)
            ?.let(Utils::hexStringToByteArray)
            ?: ByteArray(0)

    override fun getJSONArray(key: String): JSONArray =
        try {
            JSONArray(sharedPreferences.getString(key, "[]"))
        } catch (e: Exception) {
            logger.error("Failed to decode JSON array", e)
            JSONArray()
        }

    override fun getJSONObject(key: String): JSONObject? =
        try {
            sharedPreferences.getString(key, "[]")
                ?.let(::JSONObject)
        } catch (e: Exception) {
            logger.error("Failed to decode JSON Object", e)
            null
        }

    override fun clear() {
        sharedPreferences.edit {
            clear()
        }
    }

    override fun getStringSet(key: String): Set<String>? =
        if (sharedPreferences.contains(key)) {
            sharedPreferences.getStringSet(key, emptySet())
        } else {
            null
        }

    override fun containsKey(key: String): Boolean =
        sharedPreferences.contains(key)
}
