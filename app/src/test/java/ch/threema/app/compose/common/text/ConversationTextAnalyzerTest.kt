/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.compose.common.text

import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result
import ch.threema.domain.types.Identity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ConversationTextAnalyzerTest {

    @Test
    fun `finds no emojis if no emojis exist`() {
        // arrange
        val rawInput = "Hey you, could you please send me the file?"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 0)
    }

    @Test
    fun `finds single char emoji`() {
        // arrange
        val rawInput = "\u2764" // ❤

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertEmoji(startsAtIndex = 0, ofLength = 1)
        }
    }

    @Test
    fun `finds emoji with two chars`() {
        // arrange
        val rawInput = "\uD83D\uDC95" // 💕

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertEmoji(startsAtIndex = 0, ofLength = 2)
        }
    }

    @Test
    fun `finds emoji with eight chars`() {
        // arrange
        val rawInput = "\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC68" // 👨‍❤️‍👨

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertEmoji(startsAtIndex = 0, ofLength = 8)
        }
    }

    @Test
    fun `finds chained emojis`() {
        // arrange
        val rawInput = "\uD83C\uDFD4\uD83D\uDC95" // 🏔💕

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 2) {
            assertEmoji(startsAtIndex = 0, ofLength = 2)
            assertEmoji(startsAtIndex = 2, ofLength = 2)
        }
    }

    @Test
    fun `finds multiple emojis in between text`() {
        // arrange
        // "Hey look at this mountain 🏔, the double hearts 💕 and this old-school heart emoji ❤. Cool right?"
        val rawInput =
            "Hey look at this mountain \uD83C\uDFD4, the double hearts \uD83D\uDC95 and this old-school heart emoji \u2764. Cool right? Trademark: ™"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 3) {
            assertEmoji(startsAtIndex = 26, ofLength = 2)
            assertEmoji(startsAtIndex = 48, ofLength = 2)
            assertEmoji(startsAtIndex = 83, ofLength = 1)
        }
    }

    @Test
    fun `finds single mention if no other content exists`() {
        // arrange
        val rawInput = "@[0123ABCD]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertMention(startsAtIndex = 0, forIdentity = "0123ABCD")
        }
    }

    @Test
    fun `finds mentions if no other content exists`() {
        // arrange
        val rawInput = "@[0123ABCD]@[3210DCBA]@[@@@@@@@@]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 3) {
            assertMention(startsAtIndex = 0, forIdentity = "0123ABCD")
            assertMention(startsAtIndex = 11, forIdentity = "3210DCBA")
            assertMention(startsAtIndex = 22, forIdentity = "@@@@@@@@")
        }
    }

    @Test
    fun `finds mentions if no other content besides spaces exists`() {
        // arrange
        val rawInput = " @[0123ABCD]  @[3210DCBA]  @[@@@@@@@@]  @[*0123456]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 4) {
            assertMention(startsAtIndex = 1, forIdentity = "0123ABCD")
            assertMention(startsAtIndex = 14, forIdentity = "3210DCBA")
            assertMention(startsAtIndex = 27, forIdentity = "@@@@@@@@")
            assertMention(startsAtIndex = 40, forIdentity = "*0123456")
        }
    }

    @Test
    fun `finds mention and ignores other invalid`() {
        // arrange
        // the "@[@@@@@@@@@]" contains one "@" too much
        val rawInput = "@[0123ABCD], @[SHORT] and @[@@@@@@@@@] could you please send me the file?"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertMention(startsAtIndex = 0, forIdentity = "0123ABCD")
        }
    }

    @Test
    fun `finds all mention types in between text`() {
        // arrange
        val rawInput = "Text @[0123ABCD] text @[@@@@@@@@] text @[*0123456] Prefix"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 3) {
            assertMention(startsAtIndex = 5, forIdentity = "0123ABCD")
            assertMention(startsAtIndex = 22, forIdentity = "@@@@@@@@")
            assertMention(startsAtIndex = 39, forIdentity = "*0123456")
        }
    }

    @Test
    fun `finds no mentions when no mentions exist`() {
        // arrange
        val rawInput = "Could you please send me the file?"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 0)
    }

    @Test
    fun `finds emoji and mention`() {
        // arrange
        val rawInput = "Start \uD83C\uDF36 mid @[0123ABCD] end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 2) {
            assertEmoji(startsAtIndex = 6, ofLength = 2)
            assertMention(startsAtIndex = 13, forIdentity = "0123ABCD")
        }
    }

    @Test
    fun `finds mention and emoji`() {
        // arrange
        val rawInput = "Start @[0123ABCD] mid \uD83C\uDF36 end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 2) {
            assertMention(startsAtIndex = 6, forIdentity = "0123ABCD")
            assertEmoji(startsAtIndex = 22, ofLength = 2)
        }
    }

    @Test
    fun `finds multiple mentions and emojis`() {
        // arrange
        // Start ❤ @[0123ABCD] emojis here: 💚 🌶 and more mentions here @[0123ABCD] @[0123ABCD] ending it with 💚.
        val rawInput = "Start \u2764 @[0123ABCD] emojis here: \uD83D\uDC9A \uD83C\uDF36 and more " +
            "mentions here @[0123ABCD] @[0123ABCD] ending it with \uD83D\uDC9A."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 7) {
            assertEmoji(startsAtIndex = 6, ofLength = 1)
            assertMention(startsAtIndex = 8, forIdentity = "0123ABCD")
            assertEmoji(startsAtIndex = 33, ofLength = 2)
            assertEmoji(startsAtIndex = 36, ofLength = 2)
            assertMention(startsAtIndex = 62, forIdentity = "0123ABCD")
            assertMention(startsAtIndex = 74, forIdentity = "0123ABCD")
            assertEmoji(startsAtIndex = 101, ofLength = 2)
        }
    }

    @Test
    fun `finds mentions and emojis tucked together`() {
        // arrange
        // Start @[0123ABCD]🌶@[0123ABCD]🌶🌶@[0123ABCD]@[0123ABCD] end.
        val rawInput = "Start @[0123ABCD]\uD83C\uDF36@[0123ABCD]\uD83C\uDF36\uD83C\uDF36@[0123ABCD]@[0123ABCD] end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 7) {
            assertMention(startsAtIndex = 6, forIdentity = "0123ABCD")
            assertEmoji(startsAtIndex = 17, ofLength = 2)
            assertMention(startsAtIndex = 19, forIdentity = "0123ABCD")
            assertEmoji(startsAtIndex = 30, ofLength = 2)
            assertEmoji(startsAtIndex = 32, ofLength = 2)
            assertMention(startsAtIndex = 34, forIdentity = "0123ABCD")
            assertMention(startsAtIndex = 45, forIdentity = "0123ABCD")
        }
    }

    @Test
    fun `skips mentions if disabled`() {
        // arrange
        val rawInput = "Start \uD83C\uDF36 mid @[0123ABCD] end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertEmoji(startsAtIndex = 6, ofLength = 2)
        }
    }

    companion object {

        private fun assertAnalyzeResult(result: Result, itemCount: Int, assertItems: Result.() -> Unit = {}) {
            assertEquals(itemCount, result.items.size)
            result.assertItems()
        }

        /**
         *  @param startsAtIndex Inclusive
         */
        private fun Result.assertEmoji(startsAtIndex: Int, ofLength: Int) {
            val emoji: Result.SearchResult? = this.items[startsAtIndex]
            assertNotNull(emoji, "No emoji result item found at index $startsAtIndex")
            assertIs<Result.SearchResult.Emoji>(emoji)
            assertEquals(ofLength, emoji.length)
        }

        /**
         *  @param startsAtIndex Inclusive
         */
        private fun Result.assertMention(startsAtIndex: Int, forIdentity: Identity) {
            val mention: Result.SearchResult? = items[startsAtIndex]
            assertNotNull(mention, "No mention result item found at index $startsAtIndex")
            assertIs<Result.SearchResult.Mention>(mention)
            assertEquals(forIdentity, mention.identity)
        }
    }
}
