/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.data

import java.lang.ref.WeakReference

/**
 * A map where all values are weakly referenced.
 */
class WeakValueMap<K, V> {
    private val map: MutableMap<K, WeakReference<V>> = HashMap()

    /**
     * Return the value associated with the specified key, or `null` if the key is not present
     * in the map or if the value has already been garbage collected.
     */
    @Synchronized
    fun get(key: K): V? = this.map[key]?.get()

    /**
     * Look up the value associated with the specified key. If the key is not present in the map
     * or if the value has already been garbage collected, run the [miss] function, store the value
     * in the map, and return it. Otherwise, return the looked up value directly.
     */
    @Synchronized
    fun getOrCreate(key: K, miss: () -> V?): V? {
        var value = this.get(key)
        if (value != null) {
            return value
        }
        value = miss()
        if (value != null) {
            this.map[key] = WeakReference(value)
        }
        return value
    }

    /**
     * Remove the value associated with the specified key and return it.
     */
    @Synchronized
    fun remove(key: K): V? = this.map.remove(key)?.get()
}
