package ch.threema.app.emojis

import ch.threema.app.emojis.search.Emoji
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class EmojiServiceTest {
    @Test
    fun `searching for a term returns results from index`() = runTest {
        val emojiService = EmojiService(
            preferenceService = mockk {
                every { diverseEmojiPrefs } returns emptyMap()
            },
            searchIndex = mockk {
                every { search(any(), "test") } returns listOf(
                    Emoji(
                        sequence = "\uD83C\uDF81",
                        order = 0,
                        diversities = null,
                    ),
                    Emoji(
                        sequence = "\uD83D\uDC4D",
                        order = 1,
                        diversities = listOf(
                            "\uD83D\uDC4D\uD83C\uDFFB",
                            "\uD83D\uDC4D\uD83C\uDFFC",
                        ),
                    ),
                )
            },
            recentEmojis = mockk(),
        )

        val results = emojiService.search("test")

        val expected = listOf(
            EmojiInfo(
                emojiSequence = "\uD83C\uDF81",
                diversityFlag = 0,
                diversities = null,
                displayFlag = 1,
            ),
            EmojiInfo(
                emojiSequence = "\uD83D\uDC4D",
                diversityFlag = 1,
                diversities = arrayOf(
                    "\uD83D\uDC4D\uD83C\uDFFB",
                    "\uD83D\uDC4D\uD83C\uDFFC",
                ),
                displayFlag = 1,
            ),
        )
        assertEquals(expected, results)
    }

    @Test
    fun `searching for an emoji returns the emoji`() = runTest {
        val emojiService = EmojiService(
            preferenceService = mockk {
                every { diverseEmojiPrefs } returns emptyMap()
            },
            searchIndex = mockk {
                every { search(any(), any()) } returns emptyList()
            },
            recentEmojis = mockk(),
        )

        val results = emojiService.search("\uD83E\uDD96")

        val expected = listOf(
            EmojiInfo(
                emojiSequence = "\uD83E\uDD96",
                diversityFlag = 0,
                diversities = null,
                displayFlag = 1,
            ),
        )
        assertEquals(expected, results)
    }
}
