package ch.threema.base.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Get a logger instance. Should be used by all Threema code like this:
 *
 * ```java
 * // placed at the top of the class
 * private static final Logger logger = getThreemaLogger("MyClass");
 * ```
 *
 * ```kotlin
 * // placed at the top of the file
 * private val logger = getThreemaLogger("MyClass")
 * ```
 */
fun getThreemaLogger(name: String): Logger =
    LoggerFactory.getLogger("ch.threema.$name")
