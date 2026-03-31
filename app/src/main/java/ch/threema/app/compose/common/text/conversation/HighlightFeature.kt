package ch.threema.app.compose.common.text.conversation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
sealed interface HighlightFeature {

    @Immutable
    data object Off : HighlightFeature

    /**
     * Try to find and visually highlight the first occurrence of the given text content.
     *
     * @see buildHighlightedAnnotatedConversationString
     */
    @Immutable
    data class On(
        val highlightedContent: String?,
        val ignoreCase: Boolean,
        val backgroundColor: Color,
        val foregroundColor: Color,
        val spotlight: Spotlight,
    ) : HighlightFeature

    @Immutable
    sealed interface Spotlight {

        @Immutable
        data object None : Spotlight

        /**
         *  Cut all characters before the first occurrence of [HighlightFeature.On.highlightedContent] keeping [maxCharactersBeforeHighlight]. The
         *  [prefixIndicator] indicates if characters were actually cut off.
         *
         *  Useful to show a highlights content, even though the [ConversationText] has limited screen-space or max-lines.
         *
         *  @throws IllegalStateException if [maxCharactersBeforeHighlight] is negative
         *
         *  @see applySpotlightToShowAsSnippet
         */
        @Immutable
        data class ShowAsSnippet(
            val maxCharactersBeforeHighlight: Int,
            val prefixIndicator: String? = "\u2026",
        ) : Spotlight {
            init {
                check(maxCharactersBeforeHighlight >= 0)
            }
        }
    }
}
