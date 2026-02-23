package ch.threema.app.emojis

import ch.threema.app.emojis.EmojiParser.parseAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmojiParserTest {
    @Test
    fun `parse single 1-codepoint emoji`() {
        val woman = 0x1F469
        val chars = Character.toChars(woman)!!
        val string = String(chars)
        val result = parseAt(string, 0)

        assertNotNull(result)
        assertEquals(2, result.length)
    }

    @Test
    fun `parse emoji with skintone modifier`() {
        val woman = Character.toChars(0x1F469)!!
        val mediumDark = Character.toChars(0x1F3FE)!!
        val string = String(woman) + String(mediumDark)

        val result = parseAt(string, 0)

        assertNotNull(result)
        assertEquals(4, result.length)
    }
}
