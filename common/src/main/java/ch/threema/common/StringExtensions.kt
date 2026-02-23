package ch.threema.common

import java.util.Locale

fun String.withoutLastLine(): String = dropLastWhile { it != '\n' }.dropLast(1)

fun String.lastLine(): String = takeLastWhile { it != '\n' }

fun String.takeUnlessEmpty(): String? = takeUnless { it.isEmpty() }

fun String.takeUnlessBlank(): String? = takeUnless { it.isBlank() }

fun String?.isNotNullOrBlank(): Boolean = !isNullOrBlank()

fun String.capitalize(): String =
    replaceFirstChar { it.titlecase(Locale.getDefault()) }
