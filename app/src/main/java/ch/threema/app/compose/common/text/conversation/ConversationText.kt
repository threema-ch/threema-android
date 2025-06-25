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
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ch.threema.app.R
import ch.threema.app.compose.common.emoji.AsyncEmojiImage
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.emojis.EmojiUtil.REPLACEMENT_CHARACTER
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_ANDROID
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_DEFAULT
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ConfigUtils

private const val SPAN_TAG_BACKGROUND_MENTION = "background_mention"
private const val SPAN_ANNOTATION_BACKGROUND_MENTION_INVERTED = "mention_inverted"
private const val SPAN_ANNOTATION_BACKGROUND_MENTION = "mention"

/**
 *
 *  A special [Text] composable supporting these formatting features:
 *
 *  - **Emoji Support**:
 *  Replaces every special emoji character in [rawInput] with the corresponding Threema emoji or the systems default emoji.
 *  See [EmojiSettings] on how to customize the appearance. The default parameter respects flag [ConfigUtils.emojiStyle].
 *
 *  - **Mentions Support**:
 *  When enabled, styles mentions in the raw form of `@[0123ABCD]` or `@[@@@@@@@@]` with the provided style settings from [MentionFeature.On]
 *  and publishes mention click events through [onClickedMention].
 *
 *  - **Markup** is *not* supported yet.
 *
 *  @param onClickedMention Will only be called if passed [mentionFeature] is [MentionFeature.On].
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
    onClickedMention: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    val analyzeRawInputResult: ConversationTextAnalyzer.Result = remember {
        ConversationTextAnalyzer.analyze(
            rawInput = rawInput,
            searchMentions = mentionFeature is MentionFeature.On,
        )
    }

    val effectiveFontScalingFactor: Float = remember {
        getEffectiveFontScaleFactor(
            textAnalyzerResult = analyzeRawInputResult,
            emojiUpscaling = emojiSettings.upscaling,
        )
    }

    val annotatedString: AnnotatedString = remember {
        buildAnnotatedConversationString(
            rawInput = rawInput,
            textAnalyzerResult = analyzeRawInputResult,
            emojiSettings = emojiSettings,
            mentionFeature = mentionFeature,
            context = context,
            onClickedMention = onClickedMention,
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

    return analyzeRawInputResult.emojis.associate { emojiSearchResult ->

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
                    imageVector = Icons.Rounded.Face,
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

/**
 *  @return The font scale factor that will be used effectively by the `Text` composable.
 */
private fun getEffectiveFontScaleFactor(
    textAnalyzerResult: ConversationTextAnalyzer.Result,
    emojiUpscaling: EmojiUpscaling,
): Float =
    if (
        emojiUpscaling is EmojiUpscaling.On &&
        textAnalyzerResult.containsOnlyEmojis &&
        textAnalyzerResult.emojis.size <= emojiUpscaling.maxCount
    ) {
        emojiUpscaling.factor
    } else {
        1f
    }

private fun AnnotatedString.Builder.appendAnnotatedEmoji(
    rawInput: String,
    emojiItem: ConversationTextAnalyzer.Result.SearchResult.Emoji,
    @EmojiStyle emojiStyle: Int,
) {
    if (emojiStyle == EmojiStyle_DEFAULT) {
        // Append the emoji placeholder for later bitmap substitution
        appendInlineContent(
            id = emojiItem.startIndex.toString(),
            alternateText = REPLACEMENT_CHARACTER,
        )
    } else {
        // Just append the unicode contents of this emoji because android will handle it
        append(
            rawInput.substring(
                emojiItem.startIndex until (emojiItem.startIndex + emojiItem.length),
            ),
        )
    }
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
    mentionItem: ConversationTextAnalyzer.Result.SearchResult.Mention,
    context: Context,
    mentionFeatureOn: MentionFeature.On,
    onClickedMention: ((String) -> Unit)?,
) {
    val mentionBuilder = {
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
                    mentionFeatureOn.identityNameProvider(mentionItem.identity) + "\u00A0",
                )
            }
        }
    }

    if (onClickedMention != null) {
        withLink(
            link = LinkAnnotation.Clickable(
                tag = mentionItem.identity,
                linkInteractionListener = {
                    onClickedMention(mentionItem.identity)
                },
            ),
        ) {
            mentionBuilder()
        }
    } else {
        mentionBuilder()
    }
}

/**
 *  @return An [AnnotatedString] that consists of all special items (emojis and mentions) and the rest of the text-only contents from [rawInput].
 */
private fun buildAnnotatedConversationString(
    rawInput: String,
    textAnalyzerResult: ConversationTextAnalyzer.Result,
    emojiSettings: EmojiSettings,
    mentionFeature: MentionFeature,
    context: Context,
    onClickedMention: ((String) -> Unit)?,
): AnnotatedString {
    if (textAnalyzerResult.items.isEmpty()) {
        return AnnotatedString(rawInput)
    }

    return buildAnnotatedString {
        // Append all text-only characters before the first search result item (if there is any)
        if (textAnalyzerResult.items.first().startIndex > 0) {
            append(
                rawInput.substring(0 until textAnalyzerResult.items.first().startIndex),
            )
        }

        textAnalyzerResult.items.forEachIndexed { index, searchResultItem: ConversationTextAnalyzer.Result.SearchResult ->

            when (searchResultItem) {
                is ConversationTextAnalyzer.Result.SearchResult.Emoji -> appendAnnotatedEmoji(
                    rawInput = rawInput,
                    emojiItem = searchResultItem,
                    emojiStyle = emojiSettings.style,
                )

                is ConversationTextAnalyzer.Result.SearchResult.Mention -> appendMention(
                    mentionItem = searchResultItem,
                    context = context,
                    mentionFeatureOn = mentionFeature as MentionFeature.On,
                    onClickedMention = onClickedMention,
                )
            }

            // Append all text-only characters after this special search result item (if there is any)
            val textSectionStartInclusive: Int = searchResultItem.startIndex + searchResultItem.length
            val textSectionEndExclusive: Int = textAnalyzerResult.items.getOrNull(index + 1)?.startIndex
                ?: rawInput.length
            if (textSectionEndExclusive > textSectionStartInclusive) {
                append(
                    rawInput.substring(textSectionStartInclusive until textSectionEndExclusive),
                )
            }
        }
    }
}

data class EmojiSettings(
    @EmojiStyle val style: Int,
    val upscaling: EmojiUpscaling,
) {
    internal val useSystemEmojis: Boolean = style == EmojiStyle_ANDROID
}

sealed interface EmojiUpscaling {

    data object Off : EmojiUpscaling

    /**
     * @param maxCount The maximum amount of emojis that are allowed for the upscaling to effectively happen.
     */
    data class On(
        val maxCount: Int = 3,
        val factor: Float = 2f,
    ) : EmojiUpscaling
}

object ConversationTextDefaults {

    val EmojiSettings = EmojiSettings(
        style = ConfigUtils.emojiStyle,
        upscaling = EmojiUpscaling.Off,
    )
}

sealed interface MentionFeature {

    data object Off : MentionFeature

    data class On(
        val ownIdentity: String,
        val identityNameProvider: (String) -> String,
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

@Preview(
    fontScale = 1.0f,
    group = "Font Scale Default",
)
@Preview(
    fontScale = 2.0f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    group = "Font Scale x2, Night Mode",
)
@Composable
private fun ThreemaText_Preview(
    @PreviewParameter(ThreemaTextPreviewProvider::class) rawInput: String,
) {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
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
                    identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                ),
                onClickedMention = {},
            )
        }
    }
}

@Suppress("ktlint:standard:discouraged-comment-location")
internal class ThreemaTextPreviewProvider : PreviewParameterProvider<String> {

    override val values = testDataEmojis + testDataMentions + testDataAllFeatures + testDataMarkup

    companion object {

        private val testDataMarkup = sequenceOf(
            "This is *fat* and _italic_ and ~wrong~.",
        )

        private val testDataEmojis = sequenceOf(
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

        private val testDataMentions = sequenceOf(
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

        private val testDataAllFeatures = sequenceOf(
            "Start @[0123ABCD] mid \uD83C\uDF36 end.",
            "Start \uD83C\uDF36 mid @[0123ABCD] end.",
            "Just \uD83C\uDF36 emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing text also mentioning @[0123ABCD] @[3210DCBA] \uD83C\uDFD4 " +
                "@[@@@@@@@@] and not to forget @[*0123456] \uD83C\uDFD4.",
        )

        val mentionedIdentityNameProviderPreviewImpl: (String) -> String = { identity ->
            when (identity) {
                ContactService.ALL_USERS_PLACEHOLDER_ID -> "All"
                "01234567" -> "Me"
                "0123ABCD" -> "Roberto Diaz"
                "3210DCBA" -> "Lisa Goldman"
                "*0123456" -> "Broadcast"
                else -> identity
            }
        }
    }
}
