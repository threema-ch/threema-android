package ch.threema.common

/**
 * Returns a [Map] where keys are elements from the given collection and values are produced by the [valueSelector] function applied to each element.
 * Entries where the value is null are skipped and not added to the map.
 *
 * If any two elements are equal, the last one gets added to the map.
 *
 * The returned map preserves the entry iteration order of the original collection.
 */
fun <T, V : Any> Iterable<T>.associateWithNotNull(valueSelector: (T) -> V?): Map<T, V> = buildMap {
    for (item in this@associateWithNotNull) {
        val value = valueSelector(item) ?: continue
        this[item] = value
    }
}
