package ch.threema.app.compose.common.text

import ch.threema.app.compose.common.text.conversation.ConversationTextUtil.truncateRespectingEmojis
import ch.threema.testhelpers.willThrow
import kotlin.test.assertEquals
import org.junit.Test

class ConversationTextUtilText {

    @Test
    fun `truncate should throw exception for negative max length`() {
        val testCase = {
            truncateRespectingEmojis(text = "Hello", maxLength = -1)
        }
        testCase willThrow IllegalStateException::class
    }

    @Test
    fun `truncate should handle empty string`() {
        assertEquals(
            expected = "",
            actual = truncateRespectingEmojis("", 5),
        )
    }

    @Test
    fun `truncate should handle zero max length`() {
        assertEquals(
            expected = "",
            actual = truncateRespectingEmojis("Hello", 0),
        )
    }

    @Test
    fun `truncate should return original text when length is less than max`() {
        assertEquals(
            expected = "Hello",
            actual = truncateRespectingEmojis("Hello", 10),
        )
    }

    @Test
    fun `truncate should return original text when length equals max`() {
        assertEquals(
            expected = "Hello",
            actual = truncateRespectingEmojis("Hello", 5),
        )
    }

    @Test
    fun `truncate should truncate standard text correctly`() {
        val input = "Hello"

        assertEquals(
            expected = "H",
            actual = truncateRespectingEmojis(input, 1),
        )
        assertEquals(
            expected = "Hel",
            actual = truncateRespectingEmojis(input, 3),
        )
        assertEquals(
            expected = "Hello",
            actual = truncateRespectingEmojis(input, 5),
        )
        assertEquals(
            expected = "Hello",
            actual = truncateRespectingEmojis(input, 6),
        )
        assertEquals(
            expected = "Hello",
            actual = truncateRespectingEmojis(input, 100),
        )
    }

    @Test
    fun `truncate should truncate standard text with whitespaces and line breaks correctly`() {
        val input = "Hey,\nthis is Robert."

        assertEquals(
            expected = "Hey,",
            actual = truncateRespectingEmojis(input, 4),
        )
        assertEquals(
            expected = "Hey,\n",
            actual = truncateRespectingEmojis(input, 5),
        )
        assertEquals(
            expected = "Hey,\nt",
            actual = truncateRespectingEmojis(input, 6),
        )
        assertEquals(
            expected = "Hey,\nthis is Robert.",
            actual = truncateRespectingEmojis(input, 20),
        )
        assertEquals(
            expected = "Hey,\nthis is Robert.",
            actual = truncateRespectingEmojis(input, 100),
        )
    }

    @Test
    fun `truncate handles complex emojis correctly`() {
        val input = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you? \uD83E\uDED5" // Hey 🇨🇭🇨🇭 how ware you? 🫕

        assertEquals(
            expected = "Hey",
            actual = truncateRespectingEmojis(input, 3),
        )
        assertEquals(
            expected = "Hey ",
            actual = truncateRespectingEmojis(input, 4),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED",
            actual = truncateRespectingEmojis(input, 5),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED",
            actual = truncateRespectingEmojis(input, 6),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you?",
            actual = truncateRespectingEmojis(input, 20),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you? ",
            actual = truncateRespectingEmojis(input, 21),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you? \uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 22),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you? \uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 23),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED\uD83C\uDDE8\uD83C\uDDED how ware you? \uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 100),
        )
    }

    @Test
    fun `truncate is not confused by invalid emoji sequence`() {
        val input = "Hey \uD83C\uDDE8\uD83C\uDDED \uD83C how ware you?" // Hey 🇨🇭 ? how ware you?

        assertEquals(
            expected = "Hey",
            actual = truncateRespectingEmojis(input, 3),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED",
            actual = truncateRespectingEmojis(input, 5),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED \uD83C",
            actual = truncateRespectingEmojis(input, 7),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED \uD83C ",
            actual = truncateRespectingEmojis(input, 8),
        )
        assertEquals(
            expected = "Hey \uD83C\uDDE8\uD83C\uDDED \uD83C how ware you?",
            actual = truncateRespectingEmojis(input, 100),
        )
    }

    @Test
    fun `truncate handles input that only contains emojis`() {
        // 3 emojis with different sequence lengths (4 + 2 + 2)
        val input = "\uD83C\uDDE8\uD83C\uDDED\uD83E\uDD52\uD83E\uDED5" // 🇨🇭🥒🫕

        assertEquals(
            expected = "",
            actual = truncateRespectingEmojis(input, 0),
        )
        assertEquals(
            expected = "\uD83C\uDDE8\uD83C\uDDED",
            actual = truncateRespectingEmojis(input, 1),
        )
        assertEquals(
            expected = "\uD83C\uDDE8\uD83C\uDDED\uD83E\uDD52",
            actual = truncateRespectingEmojis(input, 2),
        )
        assertEquals(
            expected = "\uD83C\uDDE8\uD83C\uDDED\uD83E\uDD52\uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 3),
        )
        assertEquals(
            expected = "\uD83C\uDDE8\uD83C\uDDED\uD83E\uDD52\uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 4),
        )
        assertEquals(
            expected = "\uD83C\uDDE8\uD83C\uDDED\uD83E\uDD52\uD83E\uDED5",
            actual = truncateRespectingEmojis(input, 100),
        )
    }
}
