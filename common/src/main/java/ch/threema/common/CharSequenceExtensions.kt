package ch.threema.common

fun CharSequence.truncate(maxLength: Int): CharSequence {
    require(maxLength >= 0)
    return if (length > maxLength) substring(0, maxLength) + "…" else this
}
