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

object ConversationTextAnalyzer {

    private const val MENTION_SEARCH_REGEX = "@\\[([0-9A-Z*@]{8})]"

    /**
     * @param items The list of all found emojis and mentions. It will preserve the order of occurrences from the given input string.
     * @param containsOnlyEmojis Is true when the only contents of input string are emoji characters. Will be false if the input is empty.
     */
    data class Result(
        val items: List<SearchResult>,
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
                val identity: String,
            ) : SearchResult {

                /**
                 *  Any valid mention string (e.g. `@[01234567]` or `@[@@@@@@@@]`) will be exactly 11 characters long.
                 */
                override val length: Int = 11

                val mentionsAll: Boolean
                    get() = identity == ContactService.ALL_USERS_PLACEHOLDER_ID
            }
        }

        val emojis: List<SearchResult.Emoji>
            get() = items.filterIsInstance<SearchResult.Emoji>()

        internal companion object {
            val blank
                get() = Result(
                    items = emptyList(),
                    containsOnlyEmojis = false,
                )
        }
    }

    /**
     *  Searches for every emoji and mention in the given [rawInput]. The result will contain a list of all found items in the **exact
     *  order** they occurred in the input.
     */
    fun analyze(
        rawInput: String,
        searchMentions: Boolean,
    ): Result =
        if (rawInput.isBlank()) {
            Result.blank
        } else {
            val emojis: List<SearchResult> = searchEmojis(rawInput)
            val mentions: List<SearchResult> = if (searchMentions) searchMentions(rawInput) else emptyList()
            val items: List<SearchResult> = (emojis + mentions).sortedBy(SearchResult::startIndex)
            Result(
                items = items,
                containsOnlyEmojis = emojis.isNotEmpty() && emojis.sumOf(SearchResult::length) == rawInput.length,
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
