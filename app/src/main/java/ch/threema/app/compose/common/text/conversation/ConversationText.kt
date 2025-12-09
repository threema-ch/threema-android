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

@file:OptIn(ExperimentalTextApi::class)

package ch.threema.app.compose.common.text.conversation

import android.content.Context
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ch.threema.android.ResolvableString
import ch.threema.android.ResolvedString
import ch.threema.app.R
import ch.threema.app.compose.common.emoji.AsyncEmojiImage
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_ANDROID
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ConfigUtils
import ch.threema.domain.types.Identity

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
 *  and publishes mention click events through [MentionFeature.On.onClickedMention].
 *
 *  - **Markup Support**:
 *  When enabled, formats bold text via `*`, italic text via `_` and strikethrough text via `~`. These format tokens can be combined and nested.
 *  See [ch.threema.app.emojis.MarkupParser]
 *
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
) {
    val context = LocalContext.current

    val analyzeRawInputResult: ConversationTextAnalyzer.Result = remember {
        ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = mentionFeature is MentionFeature.On,
        )
    }

    val effectiveFontScalingFactor: Float = remember {
        emojiSettings.upscalingFeature.getEffectiveFontScaleFactor(
            rawInputLength = rawInput.length,
            analyzeRawInputResult = analyzeRawInputResult,
        )
    }

    val annotatedString: AnnotatedString = remember {
        buildAnnotatedConversationString(
            rawInput = rawInput,
            analyzeRawInputResult = analyzeRawInputResult,
            mentionFeature = mentionFeature,
            context = context,
            emojiStyle = emojiSettings.style,
            markupEnabled = markupEnabled,
        )
    }

    val effectiveDensity = Density(
        density = LocalDensity.current.density,
        fontScale = LocalDensity.current.fontScale * effectiveFontScalingFactor,
    )

    CompositionLocalProvider(LocalDensity provides effectiveDensity) {
        val inlineContentEmojiMap: Map<String, InlineTextContent> = remember {
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

private data class MentionBackgroundSpan(
    val textLineBounds: List<TextLineBounds>,
    val color: Color,
)

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

data class EmojiSettings(
    @EmojiStyle val style: Int,
    val upscalingFeature: EmojiUpscalingFeature,
) {
    internal val useSystemEmojis: Boolean = style == EmojiStyle_ANDROID
}

sealed interface EmojiUpscalingFeature {

    fun getEffectiveFontScaleFactor(
        rawInputLength: Int,
        analyzeRawInputResult: ConversationTextAnalyzer.Result,
    ): Float = 1f

    data object Off : EmojiUpscalingFeature

    /**
     * @param maxCount The maximum amount of emojis that are allowed for the upscaling to effectively happen.
     */
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

object ConversationTextDefaults {

    val EmojiSettings = EmojiSettings(
        style = ConfigUtils.emojiStyle,
        upscalingFeature = EmojiUpscalingFeature.Off,
    )
}

sealed interface MentionFeature {

    data object Off : MentionFeature

    data class On(
        val ownIdentity: Identity,
        val identityNameProvider: (Identity) -> ResolvableString?,
        val onClickedMention: ((String) -> Unit)? = null,
        val paddingVertical: TextUnit = 1.sp,
        val cornerRadius: TextUnit = 4.sp,
    ) : MentionFeature {

        companion object {

            internal fun colors(context: Context): MentionSpanColors {
                return MentionSpanColors(
                    background = Color(ContextCompat.getColor(context, R.color.mention_background)),
                    textColor = Color(ContextCompat.getColor(context, R.color.mention_text_color)),
                    textColorAtSign = Color(ContextCompat.getColor(context, R.color.mention_text_color)).copy(
                        alpha = .3f,
                    ),
                )
            }

            internal fun colorsInverted(context: Context): MentionSpanColors {
                return MentionSpanColors(
                    background = Color(ContextCompat.getColor(context, R.color.mention_background_inverted)),
                    textColor = Color(ContextCompat.getColor(context, R.color.mention_text_color_inverted)),
                    textColorAtSign = Color(ContextCompat.getColor(context, R.color.mention_text_color_inverted)).copy(
                        alpha = .3f,
                    ),
                )
            }
        }
    }
}

data class MentionSpanColors(
    val background: Color,
    val textColor: Color,
    val textColorAtSign: Color,
)

@Suppress("ktlint:standard:discouraged-comment-location")
internal class ConversationTextPreviewProviderEmojis : PreviewParameterProviderConversationText() {

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
                    style = EmojiStyle_ANDROID,
                    upscalingFeature = EmojiUpscalingFeature.On(),
                ),
                mentionFeature = MentionFeature.Off,
                markupEnabled = false,
            )
        }
    }
}

internal class ConversationTextPreviewProviderMentions : PreviewParameterProviderConversationText() {

    override val values = sequenceOf(
        "No mentions at all in this text",
        "Mentioning myself @[01234567].",
        "Hey @[0123ABCD], how are you?",
        "Hey @[@@@@@@@@], how are you all?",
        "Hey @[0123ABCD] and @[3210DCBA], how are the two of you?",
        "@[0123ABCD] Mention at start",
        "Mention at end @[0123ABCD]",
        "Chained mentions: @[0123ABCD] @[3210DCBA] @[@@@@@@@@]",
        "@[0123ABCD] @[3210DCBA] @[@@@@@@@@] @[0123ABCD] @[3210DCBA] @[@@@@@@@@] @[0123ABCD] @[3210DCBA] @[@@@@@@@@] @[0123ABCD] " +
            "@[3210DCBA] @[@@@@@@@@] @[0123ABCD] @[3210DCBA] @[@@@@@@@@]",
        "Mention with an asterisks identity: @[*0123456]",
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
                    ownIdentity = "01234567",
                    identityNameProvider = { identity ->
                        ResolvedString(
                            when (identity) {
                                ContactService.ALL_USERS_PLACEHOLDER_ID -> "All"
                                "01234567" -> "Me"
                                "0123ABCD" -> "Roberto Diaz"
                                "3210DCBA" -> "Lisa Goldman"
                                "*0123456" -> "Broadcast"
                                else -> identity
                            },
                        )
                    },
                ),
                markupEnabled = false,
            )
        }
    }
}

internal class ConversationTextPreviewProviderMarkup : PreviewParameterProviderConversationText() {

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

internal class ConversationTextPreviewProviderAll : PreviewParameterProviderConversationText() {

    override val values = sequenceOf(
        // Emojis + Mentions
        "Start @[0123ABCD] mid \uD83C\uDF36 end.",
        "Start \uD83C\uDF36 mid @[0123ABCD] end.",
        "Just \uD83C\uDF36 emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing " +
            "text also mentioning @[0123ABCD] @[3210DCBA] \uD83C\uDFD4 " +
            "@[@@@@@@@@] and not to forget @[*0123456] \uD83C\uDFD4.",
        // Mentions + Markup
        "Word *bold @[0123ABCD]* word.",
        "Word *@[0123ABCD] bold* word.",
        "Word _italic @[0123ABCD]_ word.",
        "Word _@[0123ABCD] italic_ word.",
        "Word ~strikethrough @[0123ABCD]~ word.",
        "Word ~@[0123ABCD] strikethrough~ word.",
        "Word *_bold-italic @[0123ABCD]_* word.",
        "Word *_@[0123ABCD] bold-italic _* word.",
        "Word *_~@[0123ABCD]~_* word.",
        "Word @[0123ABCD] word *@[0123ABCD]* word _@[0123ABCD]_ word ~@[0123ABCD]~ word.",

        // Broadcast identity mention + markup
        // TODO(ANDR-4149): Check if these 4 testcases are now rendered correctly
        "Word @[*0123456] word",
        "Word @[*0123456] word* word",
        "Word *word @[*0123456] word",
        "Word *word @[*0123456] word* word",

        "Word *\uD83C\uDF36* word",
        "Word _\uD83C\uDF36_ word",
        "Word ~\uD83C\uDF36~ word",

        "Word *bold \uD83C\uDF36 bold* word",
        "Word _italic \uD83C\uDF36 italic_ word",
        "Word ~strikethrough \uD83C\uDF36 strikethrough~ word",

        "Word *_bold-italic @[0123ABCD] bold-italic \uD83C\uDFD4_ bold \uD83C\uDFD4 *. Word ~strikethrough \uD83D\uDC9A word~.",
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
                    style = EmojiStyle_ANDROID,
                ),
                mentionFeature = MentionFeature.On(
                    ownIdentity = "01234567",
                    identityNameProvider = { identity ->
                        ResolvedString(
                            when (identity) {
                                ContactService.ALL_USERS_PLACEHOLDER_ID -> "All"
                                "01234567" -> "Me"
                                "0123ABCD" -> "Roberto Diaz"
                                "3210DCBA" -> "Lisa Goldman"
                                "*0123456" -> "Broadcast"
                                else -> identity
                            },
                        )
                    },
                ),
                markupEnabled = true,
            )
        }
    }
}

internal abstract class PreviewParameterProviderConversationText : PreviewParameterProvider<String> {

    companion object {
        val mentionedIdentityNameProviderPreviewImpl: (Identity) -> ResolvableString? = { identity ->
            ResolvedString(
                when (identity) {
                    ContactService.ALL_USERS_PLACEHOLDER_ID -> "All"
                    "01234567" -> "Me"
                    "0123ABCD" -> "Roberto Diaz"
                    "3210DCBA" -> "Lisa Goldman"
                    "*0123456" -> "Broadcast"
                    else -> identity
                },
            )
        }
    }
}
