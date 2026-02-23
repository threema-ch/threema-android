package ch.threema.common

/**
 * Runs the provided [block] and always returns `true`. This is useful in the context of Android, where there are a lot of methods
 * that return a boolean to indicate whether an event or similar was handled (i.e., consumed). This allows to avoid cluttering the code
 * with dangling `return true` lines in cases where the event should always be consumed.
 */
inline fun <T> consume(block: () -> T): Boolean {
    block()
    return true
}
