package ch.threema.common

fun isClassAvailable(className: String): Boolean =
    try {
        Class.forName(className)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
