package ch.threema.app.utils

/**
 * An assertion that work at runtime, even if runtime assertions haven't been enabled
 * on the JVM using the -ea JVM option.
 */
fun runtimeAssert(value: Boolean, message: String) {
    if (!value) {
        throw AssertionError("Assertion failed: $message")
    }
}
