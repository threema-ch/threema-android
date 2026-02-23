package ch.threema.common

fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) {
        minus(item)
    } else {
        plus(item)
    }
