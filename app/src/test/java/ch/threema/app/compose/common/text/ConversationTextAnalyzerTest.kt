package ch.threema.app.compose.common.text

import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result
import ch.threema.app.services.ContactService
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import ch.threema.domain.types.Identity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testdata.TestData

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
        val rawInput = "@[${TestData.Identities.OTHER_1}]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertMention(startsAtIndex = 0, forIdentity = TestData.Identities.OTHER_1)
        }
    }

    @Test
    fun `finds mentions if no other content exists`() {
        // arrange
        val rawInput = "@[${TestData.Identities.OTHER_1}]@[${TestData.Identities.OTHER_2}]@[${ContactService.ALL_USERS_PLACEHOLDER_ID}]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 3) {
            assertMention(startsAtIndex = 0, forIdentity = TestData.Identities.OTHER_1)
            assertMention(startsAtIndex = 11, forIdentity = TestData.Identities.OTHER_2)
            assertMention(startsAtIndex = 22, forIdentity = Identity(ContactService.ALL_USERS_PLACEHOLDER_ID))
        }
    }

    @Test
    fun `finds mentions if no other content besides spaces exists`() {
        // arrange
        val rawInput =
            " @[${TestData.Identities.OTHER_1}]  @[${TestData.Identities.OTHER_2}]  @[${ContactService.ALL_USERS_PLACEHOLDER_ID}]  " +
                "@[${TestData.Identities.BROADCAST}]"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 4) {
            assertMention(startsAtIndex = 1, forIdentity = TestData.Identities.OTHER_1)
            assertMention(startsAtIndex = 14, forIdentity = TestData.Identities.OTHER_2)
            assertMention(startsAtIndex = 27, forIdentity = Identity(ContactService.ALL_USERS_PLACEHOLDER_ID))
            assertMention(startsAtIndex = 40, forIdentity = TestData.Identities.BROADCAST)
        }
    }

    @Test
    fun `finds mention and ignores other invalid`() {
        // arrange
        // the "@[@@@@@@@@@]" contains one "@" too much
        val rawInput = "@[${TestData.Identities.OTHER_1}], @[SHORT] and @[@@@@@@@@@] could you please send me the file?"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 1) {
            assertMention(startsAtIndex = 0, forIdentity = TestData.Identities.OTHER_1)
        }
    }

    @Test
    fun `finds all mention types in between text`() {
        // arrange
        val rawInput = "Text @[${TestData.Identities.OTHER_1}] text @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] text " +
            "@[${TestData.Identities.BROADCAST}] Prefix"

        // act
        val result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 3) {
            assertMention(startsAtIndex = 5, forIdentity = TestData.Identities.OTHER_1)
            assertMention(startsAtIndex = 22, forIdentity = Identity(ContactService.ALL_USERS_PLACEHOLDER_ID))
            assertMention(startsAtIndex = 39, forIdentity = TestData.Identities.BROADCAST)
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
        val rawInput = "Start \uD83C\uDF36 mid @[${TestData.Identities.OTHER_1}] end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 2) {
            assertEmoji(startsAtIndex = 6, ofLength = 2)
            assertMention(startsAtIndex = 13, forIdentity = TestData.Identities.OTHER_1)
        }
    }

    @Test
    fun `finds mention and emoji`() {
        // arrange
        val rawInput = "Start @[${TestData.Identities.OTHER_1}] mid \uD83C\uDF36 end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 2) {
            assertMention(startsAtIndex = 6, forIdentity = TestData.Identities.OTHER_1)
            assertEmoji(startsAtIndex = 22, ofLength = 2)
        }
    }

    @Test
    fun `finds multiple mentions and emojis`() {
        // arrange
        // Start ❤ @[11111111] emojis here: 💚 🌶 and more mentions here @[11111111] @[11111111] ending it with 💚.
        val rawInput = "Start \u2764 @[${TestData.Identities.OTHER_1}] emojis here: \uD83D\uDC9A \uD83C\uDF36 and more " +
            "mentions here @[${TestData.Identities.OTHER_1}] @[${TestData.Identities.OTHER_1}] ending it with \uD83D\uDC9A."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 7) {
            assertEmoji(startsAtIndex = 6, ofLength = 1)
            assertMention(startsAtIndex = 8, forIdentity = TestData.Identities.OTHER_1)
            assertEmoji(startsAtIndex = 33, ofLength = 2)
            assertEmoji(startsAtIndex = 36, ofLength = 2)
            assertMention(startsAtIndex = 62, forIdentity = TestData.Identities.OTHER_1)
            assertMention(startsAtIndex = 74, forIdentity = TestData.Identities.OTHER_1)
            assertEmoji(startsAtIndex = 101, ofLength = 2)
        }
    }

    @Test
    fun `finds mentions and emojis tucked together`() {
        // arrange
        // Start @[11111111]🌶@[11111111]🌶🌶@[11111111]@[11111111] end.
        val rawInput = "Start @[${TestData.Identities.OTHER_1}]\uD83C\uDF36@[${TestData.Identities.OTHER_1}]\uD83C\uDF36\uD83C\uDF36" +
            "@[${TestData.Identities.OTHER_1}]@[${TestData.Identities.OTHER_1}] end."

        // act
        val result: Result = ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = true,
        )

        // assert
        assertAnalyzeResult(result, itemCount = 7) {
            assertMention(startsAtIndex = 6, forIdentity = TestData.Identities.OTHER_1)
            assertEmoji(startsAtIndex = 17, ofLength = 2)
            assertMention(startsAtIndex = 19, forIdentity = TestData.Identities.OTHER_1)
            assertEmoji(startsAtIndex = 30, ofLength = 2)
            assertEmoji(startsAtIndex = 32, ofLength = 2)
            assertMention(startsAtIndex = 34, forIdentity = TestData.Identities.OTHER_1)
            assertMention(startsAtIndex = 45, forIdentity = TestData.Identities.OTHER_1)
        }
    }

    @Test
    fun `skips mentions if disabled`() {
        // arrange
        val rawInput = "Start \uD83C\uDF36 mid @[${TestData.Identities.OTHER_1}] end."

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

    private val mentionNameData = listOf(
        MentionNameData.Me(
            identity = TestData.Identities.ME,
            nickname = "nickname_me",
        ),
        MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "firstname_other_01",
            lastname = "lastname_other_01",
            nickname = "nickname_other_01",
        ),
        MentionNameData.Contact(
            identity = TestData.Identities.OTHER_2,
            firstname = "firstname_other_02",
            lastname = "lastname_other_02",
            nickname = "nickname_other_02",
        ),
        MentionNameData.Contact(
            identity = TestData.Identities.OTHER_3,
            firstname = "firstname_other_03",
            lastname = "lastname_other_03",
            nickname = "nickname_other_03",
        ),
        MentionNameData.Contact(
            identity = TestData.Identities.BROADCAST,
            firstname = "firstname_broadcast",
            lastname = "lastname_broadcast",
            nickname = "nickname_broadcast",
        ),
    )

    @Test
    fun `findResolvableMentionNames should find one mention name`() {
        // arrange
        val input = "Start @[${TestData.Identities.OTHER_1}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey(TestData.Identities.OTHER_1))
    }

    @Test
    fun `findResolvableMentionNames should find my own mention name`() {
        // arrange
        val input = "Start @[${TestData.Identities.ME}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey(TestData.Identities.ME))
    }

    @Test
    fun `findResolvableMentionNames should find mention all`() {
        // arrange
        val input = "Start @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(
            mapOf(
                Identity(ContactService.ALL_USERS_PLACEHOLDER_ID) to ResourceIdString(R.string.all),
            ),
            result,
        )
    }

    @Test
    fun `findResolvableMentionNames should find broadcast mention`() {
        // arrange
        val input = "Start @[${TestData.Identities.BROADCAST}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey(TestData.Identities.BROADCAST))
    }

    @Test
    fun `findResolvableMentionNames find mention but can not resolve the name`() {
        // arrange
        val input = "Start @[${TestData.Identities.OTHER_4}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findResolvableMentionNames finds multiple mentions`() {
        // arrange
        val input = "Start @[${TestData.Identities.OTHER_4}] mid @[${TestData.Identities.OTHER_2}]@[${TestData.Identities.OTHER_1}]" +
            "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] mid @[${TestData.Identities.ME}]."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert

        assertEquals(4, result.size)
        assertTrue(result.containsKey(TestData.Identities.OTHER_2))
        assertTrue(result.containsKey(TestData.Identities.OTHER_1))
        assertTrue(result.containsKey(Identity(ContactService.ALL_USERS_PLACEHOLDER_ID)))
        assertTrue(result.containsKey(TestData.Identities.ME))
    }

    @Test
    fun `findResolvableMentionNames returns distinct results`() {
        // arrange
        val input = "Start @[${TestData.Identities.OTHER_2}]@[${TestData.Identities.OTHER_2}] end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey(TestData.Identities.OTHER_2))
    }

    @Test
    fun `findResolvableMentionNames should not find any mention 01`() {
        // arrange
        val input = "   "

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findResolvableMentionNames should not find any mention 02`() {
        // arrange
        val input = "Start end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findResolvableMentionNames should not find any mention 03`() {
        // arrange
        val input = "@[invalid_id] @[${TestData.Identities.OTHER_4} end."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findResolvableMentionNames works with emojis`() {
        // arrange
        // Start 🏔 🏔 @[$TestData.Identities.OTHER_1_2] 🏔 end🏔.
        val input = "Start \uD83C\uDFD4 \uD83C\uDFD4@[${TestData.Identities.OTHER_2}]\uD83C\uDFD4 end\uD83C\uDFD4."

        // act
        val result = ConversationTextAnalyzer.findResolvableMentionNames(
            input = input,
            mentionNameData = mentionNameData,
            contactNameFormat = ContactNameFormat.DEFAULT,
        )

        // assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey(TestData.Identities.OTHER_2))
    }

    @Test
    fun `findResolvableMentionNames finds mention in between markup`() {
        // arrange
        val input = listOf(
            "Word *bold @[${TestData.Identities.OTHER_1}]* word.",
            "Word *@[${TestData.Identities.OTHER_1}] bold* word.",
            "Word _italic @[${TestData.Identities.OTHER_1}]_ word.",
            "Word _@[${TestData.Identities.OTHER_1}] italic_ word.",
            "Word ~strikethrough @[${TestData.Identities.OTHER_1}]~ word.",
            "Word ~@[${TestData.Identities.OTHER_1}] strikethrough~ word.",
            "Word *_bold-italic @[${TestData.Identities.OTHER_1}]_* word.",
            "Word *_@[${TestData.Identities.OTHER_1}] bold-italic _* word.",
            "Word *_~@[${TestData.Identities.OTHER_1}]~_* word.",
            "Word @[${TestData.Identities.OTHER_1}] word *@[${TestData.Identities.OTHER_1}]* word _@[${TestData.Identities.OTHER_1}]_ word " +
                "~@[${TestData.Identities.OTHER_1}]~ word.",
        )

        // act
        val results = input.map { input ->
            ConversationTextAnalyzer.findResolvableMentionNames(
                input = input,
                mentionNameData = mentionNameData,
                contactNameFormat = ContactNameFormat.DEFAULT,
            )
        }

        // assert
        results.forEach { result ->
            assertEquals(1, result.size)
            assertTrue(result.containsKey(TestData.Identities.OTHER_1))
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
