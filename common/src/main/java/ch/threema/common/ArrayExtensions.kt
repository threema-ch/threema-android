package ch.threema.common

fun <T : Comparable<T>> Array<out T>.equalsIgnoreOrder(other: Array<out T>): Boolean {
    if (size != other.size) {
        return false
    }
    return sortedArray().contentEquals(other.sortedArray())
}
