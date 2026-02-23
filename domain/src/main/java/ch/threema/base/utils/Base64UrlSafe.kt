package ch.threema.base.utils

/**
 * Url safe en-/decoding according to https://datatracker.ietf.org/doc/html/rfc3548
 */
object Base64UrlSafe {
    /**
     * Encode with a url safe base 64 alphabet. Padding characters are stripped.
     */
    fun encode(bytes: ByteArray): String {
        return Base64.encodeBytes(bytes)
            .replace("+", "-")
            .replace("/", "_")
            .trimEnd('=')
    }

    /**
     * Decode a url safe base 64 string.
     */
    fun decode(s: String): ByteArray {
        val defaultBase64 = s
            .replace("-", "+")
            .replace("_", "/")

        val padding = when (defaultBase64.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }

        return Base64.decode("$defaultBase64$padding")
    }
}
