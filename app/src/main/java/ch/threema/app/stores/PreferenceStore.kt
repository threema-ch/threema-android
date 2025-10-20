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

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

interface PreferenceStore {
    fun remove(key: String)

    fun remove(keys: Set<String>)

    fun save(key: String, value: String?)

    fun save(key: String, value: Map<String, String?>)

    /**
     * Warning: strings in array must NOT contain ";" characters.
     */
    fun save(key: String, value: Array<String>)

    /**
     * Save list preference quietly without firing a UI listener event (for use in workers or other background processing)
     * Warning: strings in array must NOT contain ";" characters.
     */
    fun saveQuietly(key: String, value: Array<String>)

    fun save(key: String, value: Int)

    fun save(key: String, value: Boolean)

    fun save(key: String, value: ByteArray)

    fun save(key: String, value: Long)

    fun save(key: String, value: Float)

    fun save(key: String, value: JSONArray)

    fun save(key: String, value: JSONObject)

    fun save(key: String, value: Instant?)

    fun getString(key: String): String?

    fun getLong(key: String) = getLong(key, defaultValue = 0L)

    fun getLong(key: String, defaultValue: Long): Long

    fun getInt(key: String) = getInt(key, defaultValue = 0)

    fun getInt(key: String, defaultValue: Int): Int

    fun getFloat(key: String) = getFloat(key, defaultValue = 0f)

    fun getFloat(key: String, defaultValue: Float): Float

    fun getBoolean(key: String) = getBoolean(key, defaultValue = false)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getBytes(key: String): ByteArray

    fun getInstant(key: String): Instant?

    fun getStringArray(key: String): Array<String>?

    fun getMap(key: String): Map<String, String?>

    @Deprecated("only kept for system update, use getMap instead")
    fun getIntMap(key: String): Map<Int, String>

    fun getJSONArray(key: String): JSONArray

    fun getJSONObject(key: String): JSONObject?

    fun getStringSet(key: String): Set<String>?

    fun containsKey(key: String): Boolean

    fun clear()

    companion object {
        const val PREFS_IDENTITY = "identity"
        const val PREFS_SERVER_GROUP = "server_group"
        const val PREFS_PUBLIC_KEY = "public_key"
        const val PREFS_PUBLIC_NICKNAME = "nickname"
        const val PREFS_LINKED_EMAIL = "linked_email"
        const val PREFS_LINKED_MOBILE = "linked_mobile"
        const val PREFS_LINKED_EMAIL_PENDING = "linked_mobile_pending" // typo
        const val PREFS_LINKED_MOBILE_PENDING = "linked_mobile_pending_since"
        const val PREFS_MOBILE_VERIFICATION_ID = "linked_mobile_verification_id"
        const val PREFS_LAST_REVOCATION_KEY_SET = "last_revocation_key_set"
        const val PREFS_REVOCATION_KEY_CHECKED = "revocation_key_checked"
        const val PREFS_MD_MEDIATOR_MAX_SLOTS = "md_mediator_max_slots"
    }
}
