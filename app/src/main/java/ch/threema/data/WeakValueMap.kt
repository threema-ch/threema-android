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
     * Update the value for the specified key
     */
    @Synchronized
    fun put(key: K, value: V) {
        this.map[key] = WeakReference(value)
    }

    /**
     * Remove the value associated with the specified key and return it.
     */
    @Synchronized
    fun remove(key: K): V? = this.map.remove(key)?.get()
}
