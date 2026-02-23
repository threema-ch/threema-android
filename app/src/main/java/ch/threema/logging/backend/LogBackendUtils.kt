package ch.threema.logging.backend

/**
 * Clean up a tag, strip unnecessary package prefixes.
 */
fun cleanTag(tag: String, prefixes: Array<String>): String {
    for (prefix in prefixes) {
        if (tag.startsWith(prefix)) {
            return tag.substring(prefix.length)
        }
    }
    return tag
}
