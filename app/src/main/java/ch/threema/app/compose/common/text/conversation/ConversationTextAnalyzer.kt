package ch.threema.app.compose.common.text.conversation

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.emojis.EmojiParser
import ch.threema.app.emojis.SpriteCoordinates
import ch.threema.app.services.ContactService
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import ch.threema.domain.types.Identity
import ch.threema.domain.types.toIdentityOrNull

object ConversationTextAnalyzer {

    private const val MENTION_SEARCH_REGEX = "@\\[([0-9A-Z*@]{8})]"

    /**
     * @param items A map of all found emojis and mentions. They key is the character index from the input string where the characters for
     * the [SearchResult] begin (inclusive).
     * @param containsOnlyEmojis Is true when the only contents of input string are emoji characters. Will be false if the input is empty.
     */
    @Immutable
    data class Result(
        val items: Map<Int, SearchResult>,
        val containsOnlyEmojis: Boolean,
    ) {

        @Immutable
        sealed interface SearchResult {

            /**
             *  The global start index within the searched text (inclusive)
             */
            val startIndex: Int

            val length: Int

            /**
             *  @param length the total character length of the (combined) emoji
             */
            @Immutable
            data class Emoji(
                override val startIndex: Int,
                override val length: Int,
                val spriteCoordinates: SpriteCoordinates,
            ) : SearchResult

            @Immutable
            data class Mention(
                override val startIndex: Int,
                val identity: Identity,
            ) : SearchResult {

                /**
                 *  Any valid mention string (e.g. `@[01234567]` or `@[@@@@@@@@]`) will be exactly 11 characters long.
                 */
                override val length: Int = 11

                val mentionsAll: Boolean
                    get() = identity.value == ContactService.ALL_USERS_PLACEHOLDER_ID
            }
        }

        internal companion object {
            val blank
                get() = Result(
                    items = emptyMap(),
                    containsOnlyEmojis = false,
                )
        }
    }

    /**
     *  Searches for every [SearchResult.Emoji] and [SearchResult.Mention] in the given [rawInput]. The result will contain a map of all found items.
     *  The key of this map is the index in the input string where the [SearchResult] character sequence begins (inclusive).
     */
    fun analyze(
        rawInput: String,
        searchMentions: Boolean,
    ): Result {
        if (rawInput.isBlank()) {
            return Result.blank
        }
        val emojis: Map<Int, SearchResult.Emoji> = searchEmojis(rawInput).associateBy(SearchResult::startIndex)
        val mentions: Map<Int, SearchResult.Mention> = if (searchMentions) {
            searchMentions(rawInput).associateBy(SearchResult::startIndex)
        } else {
            emptyMap()
        }
        val items: Map<Int, SearchResult> = emojis.plus(mentions)
        return Result(
            items = items,
            containsOnlyEmojis = emojis.isNotEmpty() && emojis.values.sumOf(SearchResult::length) == rawInput.length,
        )
    }

    fun searchEmojis(rawInput: String): List<SearchResult.Emoji> {
        if (rawInput.isBlank()) {
            return emptyList()
        }
        val emojiSearchResults = mutableListOf<SearchResult.Emoji>()
        var searchEmojisFromIndex = 0
        while (searchEmojisFromIndex < rawInput.length) {
            runCatching {
                EmojiParser.parseAt(rawInput, searchEmojisFromIndex)
            }.getOrNull()?.let { emojiSearchResult: EmojiParser.ParseResult ->
                emojiSearchResults.add(
                    SearchResult.Emoji(
                        startIndex = searchEmojisFromIndex,
                        length = emojiSearchResult.length,
                        spriteCoordinates = emojiSearchResult.coords,
                    ),
                )
                searchEmojisFromIndex += emojiSearchResult.length - 1
            }
            searchEmojisFromIndex++
        }
        return emojiSearchResults
    }

    fun searchMentions(rawInput: String): List<SearchResult.Mention> {
        if (rawInput.isBlank()) {
            return emptyList()
        }
        val mentionMatches: List<MatchResult> = Regex(MENTION_SEARCH_REGEX).findAll(rawInput).toList()
        return mentionMatches.mapNotNull { regexMatchResult ->
            val startIndex = regexMatchResult.range.first
            val identity = regexMatchResult.groups[1]?.value?.toIdentityOrNull() ?: return@mapNotNull null
            SearchResult.Mention(
                startIndex = startIndex,
                identity = identity,
            )
        }
    }

    /**
     * Search for any mentions in the given [input] and determine the correct display name for it utilising [mentionNameData].
     *
     * **Special mentions:**
     * - A mention of the special identity [ContactService.ALL_USERS_PLACEHOLDER_ID] will result in a display name value of "`All`"
     * - A mention of the current users own identity **might** result in a display name value of "`Me`". See [MentionNameData.Me.getDisplayName]
     */
    fun findResolvableMentionNames(
        input: String,
        mentionNameData: List<MentionNameData>,
        contactNameFormat: ContactNameFormat,
    ): Map<Identity, ResolvableString> {
        if (input.isBlank()) {
            return emptyMap()
        }
        val mentionedIdentities: Set<Identity> = Regex(pattern = MENTION_SEARCH_REGEX)
            .findAll(input)
            .mapNotNull { mentionMatchResult -> mentionMatchResult.groups[1]?.value?.toIdentityOrNull() }
            .toSet()
        return mentionedIdentities
            .mapNotNull { mentionedIdentity ->
                if (mentionedIdentity.value == ContactService.ALL_USERS_PLACEHOLDER_ID) {
                    mentionedIdentity to ResourceIdString(resId = R.string.all)
                } else {
                    mentionNameData
                        .firstOrNull { mentionNameData -> mentionNameData.identity == mentionedIdentity }
                        ?.getDisplayName(contactNameFormat)
                        ?.let { displayName -> mentionedIdentity to displayName }
                }
            }.toMap()
    }
}
