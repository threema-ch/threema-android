package ch.threema.app.emojis

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.emojis.search.EmojiSearchIndex
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import java.lang.Exception
import java.util.Locale
import kotlinx.coroutines.*

private val logger = getThreemaLogger("EmojiService")

@SessionScoped
class EmojiService(
    private val preferenceService: PreferenceService,
    private val searchIndex: EmojiSearchIndex,
    private val recentEmojis: EmojiRecent,
) {
    private val preferredDiversities = preferenceService.getDiverseEmojiPrefs().toMutableMap()

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
        preferenceService.setDiverseEmojiPrefs(preferredDiversities)
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
        val results = searchIndex.search(getLanguageCode(), term).map {
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
        if (results.isEmpty() && EmojiUtil.isFullyQualifiedEmoji(term)) {
            return listOf(
                EmojiInfo(
                    term,
                    EmojiSpritemap.DIVERSITY_NONE,
                    null,
                    EmojiSpritemap.DISPLAY_YES,
                ),
            )
        }
        return results
    }

    private fun getRecentEmojiSequences(): List<String> {
        return recentEmojis.recentList.toList()
    }

    private fun getLanguageCode(): String {
        return Locale.getDefault().language
    }
}
