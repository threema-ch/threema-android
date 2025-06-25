/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.emojis

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.emojis.search.EmojiSearchIndex
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.LoggingUtil
import java.lang.Exception
import java.util.Locale
import kotlinx.coroutines.*

private val logger = LoggingUtil.getThreemaLogger("EmojiService")

class EmojiService(
    private val preferenceService: PreferenceService,
    private val searchIndex: EmojiSearchIndex,
    private val recentEmojis: EmojiRecent,
) {
    private val preferredDiversities = preferenceService.diverseEmojiPrefs

    fun addToRecentEmojis(emojiSequence: String) {
        recentEmojis.add(emojiSequence)
    }

    fun removeRecentEmoji(emojiSequence: String) {
        recentEmojis.remove(emojiSequence)
    }

    fun saveRecentEmojis() {
        recentEmojis.saveToPrefs()
    }

    fun hasNoRecentEmojis(): Boolean {
        return getRecentEmojiSequences().isEmpty()
    }

    fun getRecentEmojis(): List<EmojiInfo> {
        return getRecentEmojiSequences().map {
            EmojiInfo(
                it,
                EmojiSpritemap.DIVERSITY_NONE,
                null,
                EmojiSpritemap.DISPLAY_NO,
            )
        }
    }

    fun syncRecentEmojis(): Boolean {
        return recentEmojis.syncRecents()
    }

    fun getPreferredDiversity(emojiSequence: String): String {
        return preferredDiversities[emojiSequence] ?: emojiSequence
    }

    fun setDiverseEmojiPreference(emojiParent: String, emojiSequence: String) {
        preferredDiversities[emojiParent] = emojiSequence
        preferenceService.diverseEmojiPrefs = preferredDiversities
    }

    fun isEmojiSearchAvailable(): Boolean {
        return searchIndex.supportsLanguage(getLanguageCode())
    }

    @AnyThread
    fun prepareEmojiSearch() {
        CoroutineScope(Dispatchers.IO).launch {
            logger.debug("Prepare emoji search")
            searchIndex.prepareSearchIndex(getLanguageCode())
            logger.debug("Emoji search prepared")
        }
    }

    /**
     * If the search term is not empty, a list of emojis that have indexed keywords starting with
     * the (trimmed) term are returned.
     *
     * Otherwise a list of recent emojis is returned.
     */
    suspend fun search(term: String): List<EmojiInfo> {
        return withContext(Dispatchers.IO) {
            val searchTerm = term.trim()
            if (searchTerm.isEmpty()) {
                getRecentEmojis()
            } else {
                try {
                    searchEmojis(searchTerm)
                } catch (e: Exception) {
                    logger.warn("Error while searching emojis", e)
                    emptyList()
                }
            }
        }
    }

    @WorkerThread
    private fun searchEmojis(term: String): List<EmojiInfo> {
        return searchIndex.search(getLanguageCode(), term).map {
            val (diversities, diversityFlag) = if (it.diversities?.isEmpty() != false) {
                Pair(null, EmojiSpritemap.DIVERSITY_NONE)
            } else {
                Pair(it.diversities.toTypedArray(), EmojiSpritemap.DIVERSITY_PARENT)
            }
            EmojiInfo(
                it.sequence,
                diversityFlag,
                diversities,
                EmojiSpritemap.DISPLAY_YES,
            )
        }
    }

    private fun getRecentEmojiSequences(): List<String> {
        return recentEmojis.recentList.toList()
    }

    private fun getLanguageCode(): String {
        return Locale.getDefault().language
    }
}
