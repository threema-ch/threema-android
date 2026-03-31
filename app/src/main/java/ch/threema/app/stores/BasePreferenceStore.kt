package ch.threema.app.stores

import org.json.JSONArray

abstract class BasePreferenceStore {
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
        require(none { STRING_ARRAY_SEPARATOR in it && it.isNotEmpty() })
        return joinToString(separator = STRING_ARRAY_SEPARATOR)
    }

    protected fun String.decodeToStringArray(): Array<String> =
        split(STRING_ARRAY_SEPARATOR).dropLastWhile(String::isEmpty).toTypedArray()

    companion object {
        private const val STRING_ARRAY_SEPARATOR = ";"
    }
}
