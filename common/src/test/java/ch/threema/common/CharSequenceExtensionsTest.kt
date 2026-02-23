package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharSequenceExtensionsTest {
    @Test
    fun `test truncate`() {
        assertEquals("Hello World", "Hello World".truncate(maxLength = 12))
        assertEquals("Hello Worl…", "Hello World".truncate(maxLength = 10))
        assertEquals("Hello …", "Hello World".truncate(maxLength = 6))
        assertEquals("", "".truncate(maxLength = 6))
        assertEquals("…", "Hello World".truncate(maxLength = 0))
        assertFailsWith<IllegalArgumentException> {
            "Hello World".truncate(maxLength = -1)
        }
    }
}
