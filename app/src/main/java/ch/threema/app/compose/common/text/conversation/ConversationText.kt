@file:OptIn(ExperimentalTextApi::class)

package ch.threema.app.compose.common.text.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.emoji.AsyncEmojiImage
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.preference.service.PreferenceService.Companion.EMOJI_STYLE_ANDROID
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ConfigUtils

const val SPAN_TAG_BACKGROUND_MENTION = "background_mention"
const val SPAN_ANNOTATION_BACKGROUND_MENTION_INVERTED = "mention_inverted"
const val SPAN_ANNOTATION_BACKGROUND_MENTION = "mention"

/**
 *
 *  A special [Text] composable supporting these formatting features:
 *
 *  - **Emoji Support**:
 *  Replaces every special emoji character in [rawInput] with the corresponding Threema emoji or the systems default emoji.
 *  See [EmojiSettings] on how to customize the appearance. The default parameter respects flag [ConfigUtils.emojiStyle].
 *
 *  - **Mentions Support**:
 *  When enabled, styles mentions in the raw form of `@[0123ABCD]` or `@[@@@@@@@@]` with the provided settings from [MentionFeature.On]
 *  and publishes mention click events through [MentionFeature.On.onClickMention].
 *
 *  - **Markup Support**:
 *  When enabled, formats bold text via `*`, italic text via `_` and strikethrough text via `~`. These format tokens can be combined and nested.
 *  See [ch.threema.app.emojis.MarkupParser]
 *
 *  - **Highlight Support**:
 *  When enabled, tries to find and highlight the given [HighlightFeature.On.highlightedContent] in [rawInput].
 */
@Composable
fun ConversationText(
    modifier: Modifier = Modifier,
    rawInput: String,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    emojiSettings: EmojiSettings = ConversationTextDefaults.EmojiSettings,
    mentionFeature: MentionFeature = MentionFeature.Off,
    markupEnabled: Boolean = true,
    highlightFeature: HighlightFeature = HighlightFeature.Off,
) {
    val context = LocalContext.current
    val analyzeRawInputResult: ConversationTextAnalyzer.Result = remember(
        key1 = rawInput,
        key2 = mentionFeature,
    ) {
        ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = mentionFeature is MentionFeature.On,
        )
    }

    val effectiveFontScalingFactor: Float = remember(
        key1 = emojiSettings.upscalingFeature,
        key2 = rawInput.length,
        key3 = analyzeRawInputResult,
    ) {
        emojiSettings.upscalingFeature.getEffectiveFontScaleFactor(
            rawInputLength = rawInput.length,
            analyzeRawInputResult = analyzeRawInputResult,
        )
    }

    val annotatedString: AnnotatedString = remember(
        rawInput,
        mentionFeature,
        emojiSettings.style,
        markupEnabled,
        highlightFeature,
    ) {
        buildAnnotatedConversationString(
            rawInput = rawInput,
            analyzeRawInputResult = analyzeRawInputResult,
            mentionFeature = mentionFeature,
            context = context,
            emojiStyle = emojiSettings.style,
            markupEnabled = markupEnabled,
            highlightFeature = highlightFeature,
        )
    }

    val effectiveDensity = Density(
        density = LocalDensity.current.density,
        fontScale = LocalDensity.current.fontScale * effectiveFontScalingFactor,
    )

    CompositionLocalProvider(LocalDensity provides effectiveDensity) {
        val inlineContentEmojiMap: Map<String, InlineTextContent> = remember(
            key1 = analyzeRawInputResult,
            key2 = textStyle,
            key3 = emojiSettings,
        ) {
            buildInlineContentEmojiMap(
                analyzeRawInputResult = analyzeRawInputResult,
                textStyle = textStyle,
                emojiSettings = emojiSettings,
            )
        }

        var mentionBackgroundSpans: List<MentionBackgroundSpan> by remember { mutableStateOf(emptyList()) }

        Text(
            modifier = modifier.drawBehind {
                if (mentionFeature is MentionFeature.On) {
                    drawMentionBackgroundSpans(
                        spans = mentionBackgroundSpans,
                        effectiveDensity = effectiveDensity,
                        mentionFeatureOn = mentionFeature,
                    )
                }
            },
            text = annotatedString,
            inlineContent = inlineContentEmojiMap,
            style = textStyle.copy(
                platformStyle = PlatformTextStyle(
                    emojiSupportMatch = EmojiSupportMatch.None,
                ),
            ),
            maxLines = maxLines,
            overflow = overflow,
            color = color,
            onTextLayout = { layoutResult ->
                if (mentionFeature is MentionFeature.On) {
                    mentionBackgroundSpans = annotatedString.getStringAnnotations(
                        tag = SPAN_TAG_BACKGROUND_MENTION,
                        start = 0,
                        end = annotatedString.length,
                    ).map { annotatedPart ->
                        MentionBackgroundSpan(
                            textLineBounds = layoutResult.getBoundingBoxes(
                                range = annotatedPart.start..annotatedPart.end,
                                maxLines = maxLines,
                            ),
                            color = if (annotatedPart.item == SPAN_ANNOTATION_BACKGROUND_MENTION_INVERTED) {
                                MentionFeature.On.colorsInverted(context).background
                            } else {
                                MentionFeature.On.colors(context).background
                            },
                        )
                    }
                }
            },
        )
    }
}

private fun DrawScope.drawMentionBackgroundSpans(
    spans: List<MentionBackgroundSpan>,
    effectiveDensity: Density,
    mentionFeatureOn: MentionFeature.On,
) {
    spans.forEach { backgroundSpanMention ->
        backgroundSpanMention.textLineBounds.forEachIndexed { index, lineBound ->

            val cornerRadiusStart = if (index == 0) {
                mentionFeatureOn.cornerRadius
            } else {
                0.sp
            }
            val cornerRadiusEnd = if (index == backgroundSpanMention.textLineBounds.size - 1 && !lineBound.clipEnd) {
                mentionFeatureOn.cornerRadius
            } else {
                0.sp
            }

            val shape: Shape = RoundedCornerShape(
                topStart = cornerRadiusStart.toDp(),
                topEnd = cornerRadiusEnd.toDp(),
                bottomEnd = cornerRadiusEnd.toDp(),
                bottomStart = cornerRadiusStart.toDp(),
            )

            val outline: Outline = shape.createOutline(
                size = lineBound.bounds.size.copy(
                    height = (lineBound.bounds.size.height - (mentionFeatureOn.paddingVertical * 2f).toPx()).coerceAtLeast(1f),
                ),
                layoutDirection = LayoutDirection.Ltr,
                density = effectiveDensity,
            )

            if (outline is Outline.Rounded) {
                translate(
                    left = lineBound.bounds.left,
                    top = (lineBound.bounds.top + mentionFeatureOn.paddingVertical.toPx()).coerceAtMost(size.height),
                ) {
                    drawPath(
                        path = Path().apply { addRoundRect(outline.roundRect) },
                        color = backgroundSpanMention.color,
                    )
                }
            }
        }
    }
}

private fun buildInlineContentEmojiMap(
    analyzeRawInputResult: ConversationTextAnalyzer.Result,
    textStyle: TextStyle,
    emojiSettings: EmojiSettings,
): Map<String, InlineTextContent> {
    if (emojiSettings.useSystemEmojis) {
        return emptyMap()
    }

    val emojis: List<SearchResult.Emoji> = analyzeRawInputResult.items.values.filterIsInstance<SearchResult.Emoji>()

    return emojis.associate { emojiSearchResult ->

        val placeholder = Placeholder(
            width = textStyle.fontSize.value.sp,
            height = textStyle.fontSize.value.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )

        emojiSearchResult.startIndex.toString() to InlineTextContent(placeholder) { alternateText ->
            if (!LocalInspectionMode.current) {
                AsyncEmojiImage(
                    modifier = Modifier.fillMaxSize(),
                    spriteCoordinates = emojiSearchResult.spriteCoordinates,
                    contentDescription = null,
                    contentLoading = { modifier ->
                        Box(
                            modifier = modifier,
                        )
                    },
                    contentFailure = { modifier ->
                        Text(
                            modifier = modifier,
                            text = alternateText,
                            style = textStyle,
                        )
                    },
                )
            } else {
                // Placeholder in Previews because AsyncEmojiImage is not previewable
                Icon(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(R.drawable.ic_tag_faces_outline),
                    contentDescription = null,
                )
            }
        }
    }
}

@Immutable
private data class MentionBackgroundSpan(
    val textLineBounds: List<TextLineBounds>,
    val color: Color,
)

@Immutable
private data class TextLineBounds(
    val bounds: Rect,
    val clipEnd: Boolean,
)

/**
 * @param range Defines the global start- and end-index from the string part you want
 * to receive the bounding boxes for.
 * @param maxLines No rectangles after this line will be returned.
 *
 * @return Bounds for multiple lines reaching for the given [range].
 */
private fun TextLayoutResult.getBoundingBoxes(
    range: IntRange,
    maxLines: Int,
): List<TextLineBounds> {
    if (maxLines <= 0) {
        return emptyList()
    }
    val startLineIndex = getLineForOffset(range.first)
    val endLineIndex = getLineForOffset(range.last)

    return (startLineIndex..endLineIndex).mapNotNull { lineIndex ->
        if (lineIndex >= maxLines) {
            return@mapNotNull null
        }
        // Set marker to clip the end if we are not done with the passed range, but we are limited by the maxLines
        val clipEnd = lineIndex < endLineIndex && lineIndex == (maxLines - 1)
        TextLineBounds(
            bounds = Rect(
                top = getLineTop(lineIndex),
                bottom = getLineBottom(lineIndex),
                left = when (lineIndex) {
                    startLineIndex -> getHorizontalPosition(range.first, usePrimaryDirection = true)
                    else -> getLineLeft(lineIndex)
                },
                right = when (lineIndex) {
                    endLineIndex -> getHorizontalPosition(range.last, usePrimaryDirection = true)
                    else -> getLineRight(lineIndex)
                },
            ),
            clipEnd = clipEnd,
        )
    }
}

@Immutable
data class EmojiSettings(
    @EmojiStyle val style: Int,
    val upscalingFeature: EmojiUpscalingFeature,
) {
    internal val useSystemEmojis: Boolean = style == EMOJI_STYLE_ANDROID
}

object ConversationTextDefaults {

    val EmojiSettings = EmojiSettings(
        style = ConfigUtils.emojiStyle,
        upscalingFeature = EmojiUpscalingFeature.Off,
    )
}

@Suppress("ktlint:standard:discouraged-comment-location")
internal class ConversationTextPreviewProviderEmojis : PreviewParameterProvider<String> {

    override val values = sequenceOf(
        "No emojis at all in this text",
        "One emoji at the end \uD83C\uDF36", // .. 🌶
        "\uD83C\uDF36 one emoji at the start", // 🌶 ..
        "\uD83C\uDF36", // 🌶
        "\uD83C\uDF36\uD83C\uDFD4", // 🌶🏔
        "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A", // 🌶🏔💚
        "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A\uD83D\uDC1F", // 🌶🏔💚🐟
        "\uD83C\uDF36 emoji at the start and end \uD83D\uDC1F", // 🌶 .. 🐟
        "Just \uD83C\uDF36 emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing text", // .. 🌶 .. 🐟 .. 💚
        "Chained emojis \uD83C\uDF36\uD83D\uDC1F between ongoing text", // .. 🌶🐟 ..
    )
}

@Preview
@Composable
private fun ConversationText_Preview_Emojis(
    @PreviewParameter(ConversationTextPreviewProviderEmojis::class) rawInput: String,
) {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.03f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            ConversationText(
                modifier = Modifier
                    .padding(all = 12.dp),
                rawInput = rawInput,
                textStyle = MaterialTheme.typography.bodyMedium,
                emojiSettings = EmojiSettings(
                    style = EMOJI_STYLE_ANDROID,
                    upscalingFeature = EmojiUpscalingFeature.On(),
                ),
                mentionFeature = MentionFeature.Off,
                markupEnabled = false,
            )
        }
    }
}

internal class ConversationTextPreviewProviderMentions : PreviewParameterProvider<String> {

    override val values = sequenceOf(
        "No mentions at all in this text",
        "Mentioning myself @[${PreviewData.IDENTITY_ME}].",
        "Hey @[${PreviewData.IDENTITY_OTHER_1}], how are you?",
        "Hey @[${ContactService.ALL_USERS_PLACEHOLDER_ID}], how are you all?",
        "Hey @[${PreviewData.IDENTITY_OTHER_1}] and @[${PreviewData.IDENTITY_OTHER_2}], how are the two of you?",
        "@[${PreviewData.IDENTITY_OTHER_1}] Mention at start",
        "Mention at end @[${PreviewData.IDENTITY_OTHER_1}]",
        "Chained mentions: @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}]",
        "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] " +
            "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] " +
            "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] " +
            "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}] " +
            "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] @[${ContactService.ALL_USERS_PLACEHOLDER_ID}]",
        "Mention with an asterisks identity: @[${PreviewData.IDENTITY_BROADCAST}]",
        "Unknown identity mention: @[${PreviewData.IDENTITY_OTHER_3}]",
    )
}

@Preview
@Composable
private fun ConversationText_Preview_Mentions(
    @PreviewParameter(ConversationTextPreviewProviderMentions::class) rawInput: String,
) {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.03f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            ConversationText(
                modifier = Modifier
                    .padding(all = 12.dp),
                rawInput = rawInput,
                textStyle = MaterialTheme.typography.bodyMedium,
                mentionFeature = MentionFeature.On(
                    ownIdentity = PreviewData.IDENTITY_ME,
                    identityDisplayNames = PreviewData.mentionNames,
                ),
                markupEnabled = false,
            )
        }
    }
}

internal class ConversationTextPreviewProviderMarkup : PreviewParameterProvider<String> {

    override val values = sequenceOf(
        // Basic
        "word",
        "*bold*",
        "_italic_",
        "~strikethrough~",
        "word word *bold* word _italic_ word ~strikethrough~",

        // Combinations
        "word *_bold-italic_* word",
        "word _*italic-bold*_ word",
        "word ~_strikethrough-italic_~ word",
        "word _~italic-strikethrough~_ word",
        "word *~bold-strikethrough~* word",
        "word *~strikethrough-bold~* word",
        "word *_~bold-italic-strikethrough~_* word",
        "word _~*italic-strikethrough-bold*~_ word",
        "word ~*_strikethrough-bold-italic_*~_ word",

        // Nested bold
        "word *bold _bold-italic_* word",
        "word *bold _bold-italic_ bold* word",
        "word *bold ~bold-strikethrough~* word",
        "word *bold ~bold-strikethrough~ bold* word",

        // Nested italic
        "word _italic *italic-bold*_ word",
        "word _italic *italic-bold* italic_ word",
        "word _italic ~italic-strikethrough~_ word",
        "word _italic ~italic-strikethrough~ italic_ word",

        // Nested strikethrough
        "word ~strikethrough *strikethrough-bold*~ word",
        "word ~strikethrough *strikethrough-bold* strikethrough~ word",
        "word ~strikethrough _strikethrough-italic_~ word",
        "word ~strikethrough _strikethrough-italic_ strikethrough~ word",

        // Nested deep
        "word *bold _bold-italic ~bold-italic-strikethrough~_*",
        "word *bold _bold-italic ~bold-italic-strikethrough~ bold-italic_ bold* word",

        "word _italic *italic-bold ~italic-bold-strikethrough~*_",
        "word _italic *italic-bold ~italic-bold-strikethrough~ italic-bold* italic_ word",

        "word ~strikethrough *strikethrough-bold _strikethrough-bold-italic_*~",
        "word ~strikethrough *strikethrough-bold _strikethrough-bold-italic_ strikethrough-bold* strikethrough~ word",

        // Wrong closing order
        "word *bold _bold*_ word",
        "word *bold ~bold*~ word",
        "word _italic *italic_* word",
        "word _italic ~italic_~ word",
        "word ~strikethrough *strikethrough~* word",
        "word ~strikethrough _strikethrough~_ word",

        // Unclosed
        "word *word",
        "word _word",
        "word ~word",
        "word *bold _bold*",
        "word *bold ~bold*",
        "word _italic *italic_",
        "word _italic ~italic_",
        "word ~strikethrough *strikethrough~",
        "word ~strikethrough _strikethrough~",

        // URL
        "word https://link.ch/a/b*c*d word",
        "word https://link.ch/a/b_c_d word",
        "word _italic https://link.ch/a/b*c*d italic_ word",
        "word _italic https://link.ch/a/b_c_d italic_ word",
        "word *bold https://link.ch/a/b_c_d bold* word",
        "word *bold https://link.ch/a/b*c*d bold* word",
        "word *_bold-italic https://link.ch/a/b*c*d bold-italic_* word",
        "word *_~bold-italic-strikethrough https://link.ch/a/b*c*d bold-italic-strikethrough~_* word",
        "word *_~bold-italic-strikethrough https://link.ch/a/b_c_d bold-italic-strikethrough~_* word",

        // Newline
        "word *_italic_ \n word* word _italic_",
        "word _*bold* \n word_ word ~strikethrough~",
        "word ~word \n word~ word _italic_",

        // Edge-cases
        "   ",
        "",
        "*",
        "**",
        "***",
        "****",
        "_",
        "__",
        "___",
        "____",
        "~",
        "~~",
        "~~~",
        "~~~~",
        "*_~*",
    )
}

@Preview
@Composable
private fun ConversationText_Preview_Markup(
    @PreviewParameter(ConversationTextPreviewProviderMarkup::class) rawInput: String,
) {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.03f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            ConversationText(
                modifier = Modifier.padding(all = 12.dp),
                rawInput = rawInput,
                textStyle = MaterialTheme.typography.bodyMedium,
                mentionFeature = MentionFeature.Off,
                markupEnabled = true,
            )
        }
    }
}

internal class ConversationTextPreviewProviderHighlight : PreviewParameterProvider<Pair<String, String>> {

    override val values = sequenceOf(
        // Basic
        "Start mid end" to "Start",
        "Start mid mid end" to "mid",
        "Start mid end" to "end",
        "Start mid end" to "Start mid",
        "Start mid end" to "mid end",
        "Start mid end" to "Start ",
        // Markup
        "Start *bold* end" to "bold",
        "Start *bold* end" to "Start bold",
        "Start *bold* end" to "bold end",
        "Start *_bold-italic_* end" to "bold-italic",
        "Start *_bold-italic_* end" to "Start bold-italic",
        "Start *_bold-italic_* end" to "bold-italic end",
        "Start *_~bold-italic-strikethrough~_* end" to "bold-italic-strikethrough",
        "Start *_~bold-italic-strikethrough~_* end" to "Start bold-italic-strikethrough",
        "Start *_~bold-italic-strikethrough~_* end" to "bold-italic-strikethrough end",
        "Start *bold* _italic_ end" to "bold italic",
        "Start *bold* _italic_ end" to "Start bold italic",
        "Start *bold* _italic_ end" to "bold italic end",
        "Start *bold* _italic_ end" to "old ita",
        // Emojis
        "Start \uD83C\uDF36 end" to "Start \uD83C\uDF36 end",
        "Start \uD83C\uDF36 end" to "Start \uD83C\uDF36",
        "Start \uD83C\uDF36 end" to "\uD83C\uDF36 end",
        "Start \uD83C\uDF36\uD83C\uDF36\uD83C\uDF36 end" to "\uD83C\uDF36",
        "Start \uD83C\uDF36\uD83C\uDF36\uD83C\uDF36 end" to "\uD83C\uDF36\uD83C\uDF36\uD83C\uDF36",
        // Mentions
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "Start",
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "mid",
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "Roberto",
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "Diaz",
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "Roberto Diaz",
        "Start @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_1}] mid end" to "Roberto Diaz",
        // Spotlight
        PreviewData.LOREM_IPSUM_WORDS_50 to "sanctus",
        "*" + PreviewData.LOREM_IPSUM_WORDS_50 + "*" to "sanctus",
    )
}

@Preview
@Composable
private fun ConversationText_Preview_Highlight(
    @PreviewParameter(ConversationTextPreviewProviderHighlight::class) input: Pair<String, String>,
) {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationText(
                modifier = Modifier
                    .padding(all = 12.dp),
                rawInput = input.first,
                textStyle = MaterialTheme.typography.bodyMedium,
                emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                    style = EMOJI_STYLE_ANDROID,
                ),
                mentionFeature = MentionFeature.On(
                    ownIdentity = PreviewData.IDENTITY_ME,
                    identityDisplayNames = PreviewData.mentionNames,
                ),
                markupEnabled = true,
                highlightFeature = HighlightFeature.On(
                    highlightedContent = input.second,
                    ignoreCase = false,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    foregroundColor = MaterialTheme.colorScheme.onPrimary,
                    spotlight = HighlightFeature.Spotlight.ShowAsSnippet(
                        maxCharactersBeforeHighlight = 30,
                    ),
                ),
            )
        }
    }
}

internal class ConversationTextPreviewProviderAll : PreviewParameterProvider<String> {

    override val values = sequenceOf(
        // Emojis + Mentions
        "Start @[${PreviewData.IDENTITY_OTHER_1}] mid \uD83C\uDF36 end.",
        "Start \uD83C\uDF36 mid @[${PreviewData.IDENTITY_OTHER_1}] end.",
        "Just \uD83C\uDF36 emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing " +
            "text also mentioning @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] \uD83C\uDFD4 " +
            "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] and not to forget @[${PreviewData.IDENTITY_BROADCAST}] \uD83C\uDFD4.",
        // Mentions + Markup
        "Word *bold @[${PreviewData.IDENTITY_OTHER_1}]* word.",
        "Word *@[${PreviewData.IDENTITY_OTHER_1}] bold* word.",
        "Word _italic @[${PreviewData.IDENTITY_OTHER_1}]_ word.",
        "Word _@[${PreviewData.IDENTITY_OTHER_1}] italic_ word.",
        "Word ~strikethrough @[${PreviewData.IDENTITY_OTHER_1}]~ word.",
        "Word ~@[${PreviewData.IDENTITY_OTHER_1}] strikethrough~ word.",
        "Word *_bold-italic @[${PreviewData.IDENTITY_OTHER_1}]_* word.",
        "Word *_@[${PreviewData.IDENTITY_OTHER_1}] bold-italic _* word.",
        "Word *_~@[${PreviewData.IDENTITY_OTHER_1}]~_* word.",
        "Word @[${PreviewData.IDENTITY_OTHER_1}] word *@[${PreviewData.IDENTITY_OTHER_1}]* word _@[${PreviewData.IDENTITY_OTHER_1}]_ word " +
            "~@[${PreviewData.IDENTITY_OTHER_1}]~ word.",

        // Broadcast identity mention + markup
        // TODO(ANDR-4149): Check if these 4 testcases are now rendered correctly
        "Word @[${PreviewData.IDENTITY_BROADCAST}] word",
        "Word @[${PreviewData.IDENTITY_BROADCAST}] word* word",
        "Word *word @[${PreviewData.IDENTITY_BROADCAST}] word",
        "Word *word @[${PreviewData.IDENTITY_BROADCAST}] word* word",

        "Word *\uD83C\uDF36* word",
        "Word _\uD83C\uDF36_ word",
        "Word ~\uD83C\uDF36~ word",

        "Word *bold \uD83C\uDF36 bold* word",
        "Word _italic \uD83C\uDF36 italic_ word",
        "Word ~strikethrough \uD83C\uDF36 strikethrough~ word",

        "Word *_bold-italic @[${PreviewData.IDENTITY_OTHER_1}] bold-italic \uD83C\uDFD4_ bold \uD83C\uDFD4 *. Word ~strikethrough \uD83D\uDC9A " +
            "word~.",
    )
}

@Preview
@Composable
private fun ConversationText_Preview_All(
    @PreviewParameter(ConversationTextPreviewProviderAll::class) rawInput: String,
) {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.03f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            ConversationText(
                modifier = Modifier
                    .padding(all = 12.dp),
                rawInput = rawInput,
                textStyle = MaterialTheme.typography.bodyMedium,
                emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                    style = EMOJI_STYLE_ANDROID,
                ),
                mentionFeature = MentionFeature.On(
                    ownIdentity = PreviewData.IDENTITY_ME,
                    identityDisplayNames = PreviewData.mentionNames,
                ),
                markupEnabled = true,
                highlightFeature = HighlightFeature.On(
                    highlightedContent = "Word.",
                    ignoreCase = true,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    foregroundColor = MaterialTheme.colorScheme.onPrimary,
                    spotlight = HighlightFeature.Spotlight.ShowAsSnippet(
                        maxCharactersBeforeHighlight = 100,
                    ),
                ),
            )
        }
    }
}
