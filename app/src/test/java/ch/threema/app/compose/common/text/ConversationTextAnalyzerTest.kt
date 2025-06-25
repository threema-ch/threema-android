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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationTextAnalyzerTest {

    @Test
    fun `finds no emojis if no emojis exist`() {
        // arrange
        val rawInput = "Hey you, could you please send me the file?"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.isEmpty())
    }

    @Test
    fun `finds single char emoji`() {
        // arrange
        val rawInput = "\u2764" // ❤

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.size == 1)
        assertEquals(0, searchResult.emojis.first().startIndex)
        assertEquals(1, searchResult.emojis.first().length)
    }

    @Test
    fun `finds emoji with two chars`() {
        // arrange
        val rawInput = "\uD83D\uDC95" // 💕

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.size == 1)
        assertEquals(0, searchResult.emojis.first().startIndex)
        assertEquals(2, searchResult.emojis.first().length)
    }

    @Test
    fun `finds emoji with eight chars`() {
        // arrange
        val rawInput = "\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC68" // 👨‍❤️‍👨

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.size == 1)
        assertEquals(0, searchResult.emojis.first().startIndex)
        assertEquals(8, searchResult.emojis.first().length)
    }

    @Test
    fun `finds chained emojis`() {
        // arrange
        val rawInput = "\uD83C\uDFD4\uD83D\uDC95" // 🏔💕

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.size == 2)
        assertEquals(0, searchResult.emojis[0].startIndex)
        assertEquals(2, searchResult.emojis[0].length)
        assertEquals(2, searchResult.emojis[1].startIndex)
        assertEquals(2, searchResult.emojis[1].length)
    }

    @Test
    fun `finds multiple emojis in between text`() {
        // arrange
        // "Hey look at this mountain 🏔, the double hearts 💕 and this old-school heart emoji ❤. Cool right?"
        val rawInput =
            "Hey look at this mountain \uD83C\uDFD4, the double hearts \uD83D\uDC95 and this old-school heart emoji \u2764. Cool right? Trademark: ™"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertTrue(searchResult.items.size == 3)
        assertEquals(26, searchResult.emojis[0].startIndex)
        assertEquals(2, searchResult.emojis[0].length)
        assertEquals(48, searchResult.emojis[1].startIndex)
        assertEquals(2, searchResult.emojis[1].length)
        assertEquals(83, searchResult.emojis[2].startIndex)
        assertEquals(1, searchResult.emojis[2].length)
    }

    @Test
    fun `finds single mention if no other content exists`() {
        // arrange
        val rawInput = "@[0123ABCD]"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        val actualMentions = searchResult.items.map { it as Result.SearchResult.Mention }
        assertContentEquals(
            listOf(
                Result.SearchResult.Mention(startIndex = 0, identity = "0123ABCD"),
            ),
            actualMentions,
        )
    }

    @Test
    fun `finds mentions if no other content exists`() {
        // arrange
        val rawInput = "@[0123ABCD]@[3210DCBA]@[@@@@@@@@]"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        val actualMentions = searchResult.items.map { it as Result.SearchResult.Mention }
        assertContentEquals(
            listOf(
                Result.SearchResult.Mention(startIndex = 0, identity = "0123ABCD"),
                Result.SearchResult.Mention(startIndex = 11, identity = "3210DCBA"),
                Result.SearchResult.Mention(startIndex = 22, identity = "@@@@@@@@"),
            ),
            actualMentions,
        )
    }

    @Test
    fun `finds mentions if no other content besides spaces exists`() {
        // arrange
        val rawInput = " @[0123ABCD]  @[3210DCBA]  @[@@@@@@@@]  @[*0123456]"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        val actualMentions = searchResult.items.map { it as Result.SearchResult.Mention }
        assertContentEquals(
            listOf(
                Result.SearchResult.Mention(startIndex = 1, identity = "0123ABCD"),
                Result.SearchResult.Mention(startIndex = 14, identity = "3210DCBA"),
                Result.SearchResult.Mention(startIndex = 27, identity = "@@@@@@@@"),
                Result.SearchResult.Mention(startIndex = 40, identity = "*0123456"),
            ),
            actualMentions,
        )
    }

    @Test
    fun `finds mention and ignores other invalid`() {
        // arrange
        // the "@[@@@@@@@@@]" contains one "@" too much
        val rawInput = "@[0123ABCD], @[SHORT] and @[@@@@@@@@@] could you please send me the file?"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        val actualMentions = searchResult.items.map { it as Result.SearchResult.Mention }
        assertContentEquals(
            listOf(
                Result.SearchResult.Mention(startIndex = 0, identity = "0123ABCD"),
            ),
            actualMentions,
        )
    }

    @Test
    fun `finds all mention types in between text`() {
        // arrange
        val rawInput = "Text @[0123ABCD] text @[@@@@@@@@] text @[*0123456] Prefix"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        val actualMentions = searchResult.items.map { it as Result.SearchResult.Mention }
        assertContentEquals(
            listOf(
                Result.SearchResult.Mention(startIndex = 5, identity = "0123ABCD"),
                Result.SearchResult.Mention(startIndex = 22, identity = "@@@@@@@@"),
                Result.SearchResult.Mention(startIndex = 39, identity = "*0123456"),
            ),
            actualMentions,
        )
    }

    @Test
    fun `finds no mentions when no mentions exist`() {
        // arrange
        val rawInput = "Could you please send me the file?"

        // act
        val searchResult = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertTrue(searchResult.items.isEmpty())
    }

    @Test
    fun `finds emoji and mention`() {
        // arrange
        val rawInput = "Start \uD83C\uDF36 mid @[0123ABCD] end."

        // act
        val searchResult: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertEquals(2, searchResult.items.size)
        assertEquals(
            6,
            (searchResult.items[0] as Result.SearchResult.Emoji).startIndex,
        )
        assertEquals(
            Result.SearchResult.Mention(startIndex = 13, identity = "0123ABCD"),
            searchResult.items[1],
        )
    }

    @Test
    fun `finds mention and emoji`() {
        // arrange
        val rawInput = "Start @[0123ABCD] mid \uD83C\uDF36 end."

        // act
        val searchResult: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertEquals(2, searchResult.items.size)
        assertIs<Result.SearchResult.Mention>(searchResult.items[0])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[1])
        assertEquals(
            Result.SearchResult.Mention(startIndex = 6, identity = "0123ABCD"),
            searchResult.items[0],
        )
        assertEquals(
            22,
            (searchResult.items[1] as Result.SearchResult.Emoji).startIndex,
        )
        assertEquals(
            2,
            (searchResult.items[1] as Result.SearchResult.Emoji).length,
        )
    }

    @Test
    fun `finds multiple mentions and emojis`() {
        // arrange
        // Start ❤ @[0123ABCD] emojis here: 💚 🌶 and more mentions here @[0123ABCD] @[0123ABCD] ending it with 💚.
        val rawInput = "Start \u2764 @[0123ABCD] emojis here: \uD83D\uDC9A \uD83C\uDF36 and more " +
            "mentions here @[0123ABCD] @[0123ABCD] ending it with \uD83D\uDC9A."

        // act
        val searchResult: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertEquals(7, searchResult.items.size)
        assertIs<Result.SearchResult.Emoji>(searchResult.items[0])
        assertIs<Result.SearchResult.Mention>(searchResult.items[1])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[2])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[3])
        assertIs<Result.SearchResult.Mention>(searchResult.items[4])
        assertIs<Result.SearchResult.Mention>(searchResult.items[5])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[6])
    }

    @Test
    fun `finds mentions and emojis tucked together`() {
        // arrange
        // Start @[0123ABCD]🌶@[0123ABCD]🌶🌶@[0123ABCD]@[0123ABCD] end.
        val rawInput = "Start @[0123ABCD]\uD83C\uDF36@[0123ABCD]\uD83C\uDF36\uD83C\uDF36@[0123ABCD]@[0123ABCD] end."

        // act
        val searchResult: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertEquals(7, searchResult.items.size)
        assertIs<Result.SearchResult.Mention>(searchResult.items[0])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[1])
        assertIs<Result.SearchResult.Mention>(searchResult.items[2])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[3])
        assertIs<Result.SearchResult.Emoji>(searchResult.items[4])
        assertIs<Result.SearchResult.Mention>(searchResult.items[5])
        assertIs<Result.SearchResult.Mention>(searchResult.items[6])
    }

    @Test
    fun `skips mentions if disabled`() {
        // arrange
        val rawInput = "Start \uD83C\uDF36 mid @[0123ABCD] end."

        // act
        val searchResult: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = false,
        )

        // assert
        assertEquals(1, searchResult.items.size)
        assertEquals(
            6,
            (searchResult.items[0] as Result.SearchResult.Emoji).startIndex,
        )
    }
}
