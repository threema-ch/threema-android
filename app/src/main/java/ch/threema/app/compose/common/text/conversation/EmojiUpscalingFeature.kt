package ch.threema.app.compose.common.text.conversation

import androidx.compose.runtime.Immutable
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult

@Immutable
sealed interface EmojiUpscalingFeature {

    fun getEffectiveFontScaleFactor(
        rawInputLength: Int,
        analyzeRawInputResult: ConversationTextAnalyzer.Result,
    ): Float = 1f

    @Immutable
    data object Off : EmojiUpscalingFeature

    /**
     * @param maxCount The maximum amount of emojis that are allowed for the upscaling to effectively happen.
     */
    @Immutable
    data class On(
        private val maxCount: Int = 3,
        private val factor: Float = 2f,
    ) : EmojiUpscalingFeature {

        override fun getEffectiveFontScaleFactor(
            rawInputLength: Int,
            analyzeRawInputResult: ConversationTextAnalyzer.Result,
        ): Float {
            val emojis: List<SearchResult.Emoji> = analyzeRawInputResult.items.values.filterIsInstance<SearchResult.Emoji>()
            val containsOnlyEmojis: Boolean = emojis.isNotEmpty() && emojis.sumOf(SearchResult.Emoji::length) == rawInputLength
            return if (containsOnlyEmojis && emojis.size <= maxCount) factor else 1f
        }
    }
}
