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

interface EncryptedPreferenceStore : PreferenceStore {
    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun remove(keys: Set<String>) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Int) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Boolean) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Long) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Float) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getLong(key: String, defaultValue: Long): Long {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getInt(key: String, defaultValue: Int): Int {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getFloat(key: String, defaultValue: Float): Float {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getStringSet(key: String): Set<String>? {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFS_PRIVATE_KEY = "private_key"
        const val PREFS_MD_PROPERTIES = "md_properties"
    }
}
