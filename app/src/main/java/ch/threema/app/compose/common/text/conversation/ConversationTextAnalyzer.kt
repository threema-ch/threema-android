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

package ch.threema.app.compose.common.text.conversation

import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.emojis.EmojiParser
import ch.threema.app.emojis.SpriteCoordinates
import ch.threema.app.services.ContactService
import ch.threema.domain.types.Identity

object ConversationTextAnalyzer {

    private const val MENTION_SEARCH_REGEX = "@\\[([0-9A-Z*@]{8})]"

    /**
     * @param items A map of all found emojis and mentions. They key is the character index from the input string where the characters for
     * the [SearchResult] begin (inclusive).
     * @param containsOnlyEmojis Is true when the only contents of input string are emoji characters. Will be false if the input is empty.
     */
    data class Result(
        val items: Map<Int, SearchResult>,
        val containsOnlyEmojis: Boolean,
    ) {

        sealed interface SearchResult {

            /**
             *  The global start index within the searched text (inclusive)
             */
            val startIndex: Int

            val length: Int

            /**
             *  @param length the total character length of the (combined) emoji
             */
            data class Emoji(
                override val startIndex: Int,
                override val length: Int,
                val spriteCoordinates: SpriteCoordinates,
            ) : SearchResult

            data class Mention(
                override val startIndex: Int,
                val identity: Identity,
            ) : SearchResult {

                /**
                 *  Any valid mention string (e.g. `@[01234567]` or `@[@@@@@@@@]`) will be exactly 11 characters long.
                 */
                override val length: Int = 11

                val mentionsAll: Boolean
                    get() = identity == ContactService.ALL_USERS_PLACEHOLDER_ID
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
        val emojis: Map<Int, SearchResult> = searchEmojis(rawInput).associateBy(SearchResult::startIndex)
        val mentions: Map<Int, SearchResult> = if (searchMentions) {
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

    private fun searchEmojis(rawInput: String): List<SearchResult> {
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

    private fun searchMentions(rawInput: String): List<SearchResult> {
        if (rawInput.isBlank()) {
            return emptyList()
        }
        val mentionMatches: List<MatchResult> = Regex(MENTION_SEARCH_REGEX).findAll(rawInput).toList()
        return mentionMatches.mapNotNull { regexMatchResult ->
            val startIndex = regexMatchResult.range.first
            val identity = regexMatchResult.groups[1]?.value ?: return@mapNotNull null
            SearchResult.Mention(
                startIndex = startIndex,
                identity = identity,
            )
        }
    }
}
