package ch.threema.logging.backend

import kotlin.test.Test
import kotlin.test.assertEquals

class LogBackendUtilsTest {
    @Test
    fun `clean tag`() {
        val prefixes = arrayOf(
            "ch.threema.",
        )

        assertEquals("", cleanTag("", prefixes))
        assertEquals("Tag", cleanTag("Tag", prefixes))
        assertEquals("tagging.Tag", cleanTag("tagging.Tag", prefixes))
        assertEquals("tagging.Tag", cleanTag("ch.threema.tagging.Tag", prefixes))
        assertEquals("ch.threema.Tag", cleanTag("ch.threema.ch.threema.Tag", prefixes))
    }
}
