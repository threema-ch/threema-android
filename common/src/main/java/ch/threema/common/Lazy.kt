package ch.threema.common

import java.util.function.Supplier

/**
 * Intended to be used in java code to fill the missing gap of lazy initialized properties.
 */
fun <T> lazy(supplier: Supplier<T>): Lazy<T> = kotlin.lazy { supplier.get() }
