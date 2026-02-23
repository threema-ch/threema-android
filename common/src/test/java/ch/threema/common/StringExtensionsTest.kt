package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtensionsTest {
    @Test
    fun `string without last line`() {
        assertEquals("", "Hello World".withoutLastLine())
        assertEquals("Hello", "Hello\nWorld".withoutLastLine())
        assertEquals("Hello\nWorld", "Hello\nWorld\n".withoutLastLine())
        assertEquals("", "".withoutLastLine())
    }

    @Test
    fun `last line of string`() {
        assertEquals("Hello World", "Hello World".lastLine())
        assertEquals("World", "Hello\nWorld".lastLine())
        assertEquals("", "Hello\nWorld\n".lastLine())
        assertEquals("", "".lastLine())
    }

    @Test
    fun `take string unless empty`() {
        assertEquals("test", "test".takeUnlessEmpty())
        assertNull("".takeUnlessEmpty())
    }

    @Test
    fun `take string unless blank`() {
        assertEquals("test", "test".takeUnlessBlank())
        assertEquals(" test ", " test ".takeUnlessBlank())
        assertEquals(" te st ", " te st ".takeUnlessBlank())
        assertNull("".takeUnlessBlank())
        assertNull("  ".takeUnlessBlank())
    }

    @Test
    fun `test capitalize`() {
        assertEquals("Hello worlD", "hello worlD".capitalize())
        assertEquals("Hello World", "Hello World".capitalize())
        assertEquals("Äöü", "äöü".capitalize())
    }
}
