package ch.threema.app.utils

fun interface ThrowingConsumer<T> {
    @Throws(Exception::class)
    fun accept(value: T)
}
