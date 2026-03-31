package ch.threema.common

fun CharSequence.truncate(maxLength: Int): CharSequence {
    require(maxLength >= 0)
    return if (length > maxLength) substring(0, maxLength) + "…" else this
}

fun CharSequence.indexOfOrNull(string: String, startIndex: Int = 0, ignoreCase: Boolean = false): Int? =
    indexOf(
        string = string,
        startIndex = startIndex,
        ignoreCase = ignoreCase,
    ).takeIf { it != -1 }
