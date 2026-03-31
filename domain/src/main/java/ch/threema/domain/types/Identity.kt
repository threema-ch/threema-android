package ch.threema.domain.types

import androidx.compose.runtime.Immutable
import ch.threema.domain.protocol.csp.ProtocolDefines

/**
 * Represents an Identity a.k.a. a Threema ID.
 */
@JvmInline
@Immutable
value class Identity(val value: IdentityString) {
    init {
        require(value.length == ProtocolDefines.IDENTITY_LEN)
        require(
            value.all { char ->
                char.isLatinUpperCaseLetter() || char.isDigit() || char == '*' || char == '@'
            },
        )
    }

    private fun Char.isLatinUpperCaseLetter() =
        this in 'A'..'Z'

    override fun toString() = value
}

fun String.toIdentityOrNull(): Identity? =
    try {
        Identity(this)
    } catch (_: IllegalArgumentException) {
        null
    }
