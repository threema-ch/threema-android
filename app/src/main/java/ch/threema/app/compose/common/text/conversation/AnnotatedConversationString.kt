package ch.threema.app.compose.common.text.conversation

import android.content.Context
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.emojis.MarkupParser
import ch.threema.app.emojis.MarkupParser.MarkupParserException
import ch.threema.app.preference.service.PreferenceService.Companion.EMOJI_STYLE_DEFAULT
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.indexOfOrNull
import ch.threema.common.takeUnlessBlank

private val logger = getThreemaLogger("AnnotatedConversationString")

private val styleBold = SpanStyle(fontWeight = FontWeight.Bold)
private val styleItalic = SpanStyle(fontStyle = FontStyle.Italic)
private val styleStrikeThrough = SpanStyle(textDecoration = TextDecoration.LineThrough)

/**
 *  @param rawInput containing all `markup`, `mention` and `emoji` characters in their raw form
 */
fun buildAnnotatedConversationString(
    rawInput: CharSequence,
    analyzeRawInputResult: ConversationTextAnalyzer.Result,
    mentionFeature: MentionFeature,
    context: Context,
    @EmojiStyle emojiStyle: Int,
    markupEnabled: Boolean,
    highlightFeature: HighlightFeature,
): AnnotatedString {
    val markupSpans: Map<Int, MarkupParser.SpanItem> = if (markupEnabled) getAllMarkupSpans(rawInput) else emptyMap()
    var rawInputIndex = 0
    val annotatedString = buildAnnotatedString {
        while (rawInputIndex < rawInput.length) {
            val currentChar: Char = rawInput[rawInputIndex]

            // Check if we have a special sequence item (emoji or mention) starting at the current index
            val specialSequenceItem: SearchResult? = analyzeRawInputResult.items[rawInputIndex]
            if (specialSequenceItem != null) {
                when (specialSequenceItem) {
                    is SearchResult.Emoji -> appendEmoji(
                        rawInput = rawInput,
                        emojiItem = specialSequenceItem,
                        emojiStyle = emojiStyle,
                    )

                    is SearchResult.Mention -> {
                        if (mentionFeature !is MentionFeature.On) {
                            append(currentChar)
                            rawInputIndex++
                            continue
                        }
                        appendMention(
                            mentionItem = specialSequenceItem,
                            context = context,
                            mentionFeatureOn = mentionFeature,
                        )
                    }
                }

                rawInputIndex += specialSequenceItem.length
                continue
            }

            val markupSpanItem: MarkupParser.SpanItem? = markupSpans[rawInputIndex]
            when {
                markupSpanItem?.markerStart == rawInputIndex -> pushStyle(getStyleFromSpanItem(markupSpanItem))
                markupSpanItem?.markerEnd == rawInputIndex -> {
                    // TODO(ANDR-4149): Remove try-catch around pop() and never append the currentChar in this state
                    try {
                        pop()
                    } catch (illegalStateException: IllegalStateException) {
                        logger.warn("Tried to pop style from empty style stack", illegalStateException)
                        append(currentChar)
                    }
                }

                else -> append(currentChar)
            }
            rawInputIndex++
        }
    }
    return if (highlightFeature is HighlightFeature.On) {
        buildHighlightedAnnotatedConversationString(
            base = annotatedString,
            highlightFeatureOn = highlightFeature,
        )
    } else {
        annotatedString
    }
}

/**
 *  Applies the highlight-span-style to the given [AnnotatedString] if the requested highlight text content is found.
 *
 *  If the given [HighlightFeature.On.highlightedContent] is just blank, no style will be applied.
 */
private fun buildHighlightedAnnotatedConversationString(
    base: AnnotatedString,
    highlightFeatureOn: HighlightFeature.On,
): AnnotatedString {
    val effectiveHighlightedContent: String = highlightFeatureOn.highlightedContent?.takeUnlessBlank() ?: return base
    val highlightStartIndex: Int = base
        .text
        .indexOfOrNull(string = effectiveHighlightedContent, ignoreCase = highlightFeatureOn.ignoreCase)
        ?: return base

    val annotatedStringHighlighted = base.appendStyle(
        startIndex = highlightStartIndex,
        endIndex = highlightStartIndex + effectiveHighlightedContent.length,
        style = SpanStyle(
            background = highlightFeatureOn.backgroundColor,
            color = highlightFeatureOn.foregroundColor,
        ),
    )
    return if (highlightFeatureOn.spotlight is HighlightFeature.Spotlight.ShowAsSnippet) {
        applySpotlightToShowAsSnippet(
            baseHighlighted = annotatedStringHighlighted,
            spotlightShowAsSnippet = highlightFeatureOn.spotlight,
            highlightStartIndex = highlightStartIndex,
        )
    } else {
        annotatedStringHighlighted
    }
}

private fun applySpotlightToShowAsSnippet(
    baseHighlighted: AnnotatedString,
    spotlightShowAsSnippet: HighlightFeature.Spotlight.ShowAsSnippet,
    highlightStartIndex: Int,
): AnnotatedString {
    val snippetStartIndex = (highlightStartIndex - spotlightShowAsSnippet.maxCharactersBeforeHighlight)
        .coerceAtLeast(0)
    if (snippetStartIndex == 0) {
        return baseHighlighted
    }
    val emojiRanges: List<IntRange> = ConversationTextAnalyzer
        .searchEmojis(baseHighlighted.text)
        .map { searchResultEmoji ->
            searchResultEmoji.startIndex..(searchResultEmoji.startIndex + searchResultEmoji.length)
        }
    // We have to make sure to not cut in between an ongoing emoji char sequence
    val safeSplitIndex: Int = emojiRanges
        .firstOrNull { emojiRange -> snippetStartIndex in emojiRange }
        ?.start
        ?: snippetStartIndex
    if (safeSplitIndex == 0) {
        return baseHighlighted
    }
    val highlightedSnippet = baseHighlighted.subSequence(safeSplitIndex, baseHighlighted.length)
    return if (spotlightShowAsSnippet.prefixIndicator != null) {
        AnnotatedString(spotlightShowAsSnippet.prefixIndicator) + highlightedSnippet
    } else {
        highlightedSnippet
    }
}

/**
 * Append a [SpanStyle] for the given range defined by [startIndex] and [endIndex].
 *
 * @param style [SpanStyle] to be applied
 * @param startIndex the inclusive starting offset of the range
 * @param endIndex the exclusive end offset of the range
 *
 * @see AnnotatedString.Builder.addStyle
 */
private fun AnnotatedString.appendStyle(
    startIndex: Int,
    endIndex: Int,
    style: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    append(this@appendStyle)
    addStyle(
        start = startIndex,
        end = endIndex,
        style = style,
    )
}

/**
 *  Builds a map with all span-starts and span-ends. So per result item from [MarkupParser.buildSpanStack] this map will contain 2 entries
 *  (start and end).
 *
 *  `Map Key:` Character index from [rawInput] where the [MarkupParser.MarkupToken] begins or ends.
 *
 *  - Markup tokens of type [MarkupParser.TokenType.TEXT] and [MarkupParser.TokenType.NEWLINE] will not be included
 *  - Spans that do not include any text content between them will not be included, to have an input of e.g. `**` render as literals
 *
 *  In case the [MarkupParser] fails to parse the given [rawInput], an empty map is returned.
 */
private fun getAllMarkupSpans(rawInput: CharSequence): Map<Int, MarkupParser.SpanItem> {
    if (rawInput.isBlank()) {
        return emptyMap()
    }
    val markupParser: MarkupParser = MarkupParser.getInstance()
    val tokens: ArrayList<MarkupParser.MarkupToken> = markupParser.tokenize(rawInput)
    val allSpans: List<MarkupParser.SpanItem> = try {
        markupParser.buildSpanStack(tokens).toList()
    } catch (markupParserException: MarkupParserException) {
        logger.warn("Failed to parse markup tokens. All tokens will be displayed as literals.", markupParserException)
        return emptyMap()
    }
    val spanMap: MutableMap<Int, MarkupParser.SpanItem> = mutableMapOf()
    allSpans
        .filter { spanItem ->
            // We are only interested in markup spans
            when (spanItem.kind) {
                MarkupParser.TokenType.ASTERISK, MarkupParser.TokenType.UNDERSCORE, MarkupParser.TokenType.TILDE -> true
                MarkupParser.TokenType.TEXT, MarkupParser.TokenType.NEWLINE -> false
            }
        }
        .filter { spanItem ->
            // Exclude span items without content between them
            spanItem.textStart < spanItem.textEnd
        }
        .forEach { spanItem ->
            spanMap[spanItem.markerStart] = spanItem
            spanMap[spanItem.markerEnd] = spanItem
        }
    return spanMap
}

private fun getStyleFromSpanItem(spanItem: MarkupParser.SpanItem): SpanStyle =
    when (spanItem.kind) {
        MarkupParser.TokenType.TEXT -> error("Unexpected span item of type text")
        MarkupParser.TokenType.NEWLINE -> error("Unexpected span item of type new line")
        MarkupParser.TokenType.ASTERISK -> styleBold
        MarkupParser.TokenType.UNDERSCORE -> styleItalic
        MarkupParser.TokenType.TILDE -> styleStrikeThrough
    }

/**
 *  Styling the mentions is a two steps process:
 *  - First we apply the click listener and the text color here
 *  - We mark the whole content of this mention text with the [SPAN_TAG_BACKGROUND_MENTION] tag to later be able to draw the background.
 *  Because we need to draw a background with rounded corners this can not be achieved here with a Compose [SpanStyle]. To achieve it we later
 *  use the [Modifier.drawBehind] of the [Text] composable.
 *
 *  Mentions of `@All` and `@Me` will be style with the inverted mention colors.
 */
private fun AnnotatedString.Builder.appendMention(
    mentionItem: SearchResult.Mention,
    context: Context,
    mentionFeatureOn: MentionFeature.On,
) {
    val mentionBuilder = mentionBuilderLambda@{
        val identityNameResolved: String = mentionFeatureOn.identityDisplayNames[mentionItem.identity]?.get(context)
            ?: mentionItem.identity.value

        withAnnotation(
            tag = SPAN_TAG_BACKGROUND_MENTION,
            annotation = when {
                mentionItem.mentionsAll -> SPAN_ANNOTATION_BACKGROUND_MENTION_INVERTED
                mentionItem.identity == mentionFeatureOn.ownIdentity -> SPAN_ANNOTATION_BACKGROUND_MENTION_INVERTED
                else -> SPAN_ANNOTATION_BACKGROUND_MENTION
            },
        ) {
            withStyle(
                style = SpanStyle(
                    color = when {
                        mentionItem.mentionsAll -> MentionFeature.On.colorsInverted(context).textColorAtSign
                        mentionItem.identity == mentionFeatureOn.ownIdentity -> MentionFeature.On.colorsInverted(context).textColorAtSign
                        else -> MentionFeature.On.colors(context).textColorAtSign
                    },
                ),
            ) {
                append("\u00A0@")
            }
            withStyle(
                style = SpanStyle(
                    color = when {
                        mentionItem.mentionsAll -> MentionFeature.On.colorsInverted(context).textColor
                        mentionItem.identity == mentionFeatureOn.ownIdentity -> MentionFeature.On.colorsInverted(context).textColor
                        else -> MentionFeature.On.colors(context).textColor
                    },
                ),
            ) {
                append(
                    identityNameResolved + "\u00A0",
                )
            }
        }
    }

    if (mentionFeatureOn.onClickMention != null) {
        withLink(
            link = LinkAnnotation.Clickable(
                tag = mentionItem.identity.value,
                linkInteractionListener = {
                    mentionFeatureOn.onClickMention(mentionItem.identity)
                },
            ),
        ) {
            mentionBuilder()
        }
    } else {
        mentionBuilder()
    }
}

private fun AnnotatedString.Builder.appendEmoji(
    @EmojiStyle emojiStyle: Int,
    emojiItem: SearchResult.Emoji,
    rawInput: CharSequence,
) {
    val emojiSequence = rawInput.substring(
        startIndex = emojiItem.startIndex,
        endIndex = emojiItem.startIndex + emojiItem.length,
    )
    if (emojiStyle == EMOJI_STYLE_DEFAULT) {
        // Append the emoji character sequence marked with an inline-content id for later bitmap substitution,
        // To support highlighting/searching with emojis, we have to set the alternateText to the actual emoji character sequence even though it is
        // never used to display the emoji
        appendInlineContent(
            id = emojiItem.startIndex.toString(),
            alternateText = emojiSequence,
        )
    } else {
        // Just append emoji character sequence because android will handle it
        append(emojiSequence)
    }
}
