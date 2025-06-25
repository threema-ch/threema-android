/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.debug

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.ConversationTextDefaults
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.common.text.conversation.ThreemaTextPreviewProvider
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_ANDROID
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_DEFAULT
import ch.threema.app.utils.Toaster

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class PatternLibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ThreemaTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                            ),
                            modifier = Modifier.shadow(elevation = GridUnit.x1),
                            title = {
                                Text("Pattern Library")
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { finish() },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { scaffoldContentPadding ->

                    val colorCategories: List<ColorSection> = listOf(
                        ColorSection(
                            "Brand",
                            listOf(
                                MaterialTheme.colorScheme.primary to "primary",
                                MaterialTheme.colorScheme.onPrimary to "onPrimary",
                                MaterialTheme.colorScheme.secondary to "secondary",
                                MaterialTheme.colorScheme.onSecondary to "onSecondary",
                                MaterialTheme.colorScheme.tertiary to "tertiary",
                                MaterialTheme.colorScheme.onTertiary to "onTertiary",
                                MaterialTheme.colorScheme.inversePrimary to "inversePrimary",
                            ),
                        ),
                        ColorSection(
                            "Background",
                            listOf(
                                MaterialTheme.colorScheme.background to "background",
                                MaterialTheme.colorScheme.onBackground to "onBackground",
                            ),
                        ),
                        ColorSection(
                            "Container",
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer to "primaryContainer",
                                MaterialTheme.colorScheme.onPrimaryContainer to "onPrimaryContainer",
                                MaterialTheme.colorScheme.secondaryContainer to "secondaryContainer",
                                MaterialTheme.colorScheme.onSecondaryContainer to "onSecondaryContainer",
                                MaterialTheme.colorScheme.tertiaryContainer to "tertiaryContainer",
                                MaterialTheme.colorScheme.onTertiaryContainer to "onTertiaryContainer",
                            ),
                        ),
                        ColorSection(
                            "Surface",
                            listOf(
                                MaterialTheme.colorScheme.surface to "surface",
                                MaterialTheme.colorScheme.surfaceDim to "surfaceDim",
                                MaterialTheme.colorScheme.surfaceBright to "surfaceBright",
                                MaterialTheme.colorScheme.onSurface to "onSurface",
                            ),
                        ),
                        ColorSection(
                            "Surface Container",
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerLowest to "surfaceContainerLowest",
                                MaterialTheme.colorScheme.surfaceContainerLow to "surfaceContainerLow",
                                MaterialTheme.colorScheme.surfaceContainer to "surfaceContainer",
                                MaterialTheme.colorScheme.surfaceContainerHigh to "surfaceContainerHigh",
                                MaterialTheme.colorScheme.surfaceContainerHighest to "surfaceContainerHighest",
                                MaterialTheme.colorScheme.surfaceVariant to "surfaceVariant",
                                MaterialTheme.colorScheme.onSurfaceVariant to "onSurfaceVariant",
                            ),
                        ),
                        ColorSection(
                            "Inverse",
                            listOf(
                                MaterialTheme.colorScheme.inverseSurface to "inverseSurface",
                                MaterialTheme.colorScheme.inverseOnSurface to "inverseOnSurface",
                            ),
                        ),
                        ColorSection(
                            "Outline",
                            listOf(
                                MaterialTheme.colorScheme.outline to "outline",
                                MaterialTheme.colorScheme.outlineVariant to "outlineVariant",
                            ),
                        ),
                        ColorSection(
                            "Error",
                            listOf(
                                MaterialTheme.colorScheme.error to "error",
                                MaterialTheme.colorScheme.onError to "onError",
                                MaterialTheme.colorScheme.errorContainer to "errorContainer",
                                MaterialTheme.colorScheme.onErrorContainer to "onErrorContainer",
                            ),
                        ),
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = scaffoldContentPadding,
                    ) {
                        item {
                            TopLevelSectionHeader(name = "Color Scheme")
                        }

                        colorCategories.forEach { colorSection ->

                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                ) {
                                    Text(
                                        modifier = Modifier.padding(
                                            vertical = 12.dp,
                                            horizontal = GridUnit.x2,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        text = colorSection.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            items(colorSection.colors.size) { index ->
                                ColorSpot(
                                    modifier = Modifier.padding(
                                        vertical = GridUnit.x0_5,
                                        horizontal = 12.dp,
                                    ),
                                    color = colorSection.colors[index].first,
                                    colorName = colorSection.colors[index].second,
                                )
                            }
                        }

                        item {
                            Column {
                                Spacer(modifier = Modifier.height(GridUnit.x3))
                                TopLevelSectionHeader(name = "Typography")
                            }
                        }

                        item {
                            Text(
                                modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x2),
                                text = "Not yet implemented",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                            )
                        }

                        item {
                            TopLevelSectionHeader(name = "Components")
                        }

                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(
                                            vertical = GridUnit.x2,
                                            horizontal = GridUnit.x2,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    text = "ThreemaText - Emojis Default",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "This text does not contain any emojies.")
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "One emoji at the end: \uD83C\uDF36")
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36")
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A")
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A\uD83D\uDC1F")
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "Just \uD83C\uDF36 in \uD83D\uDC1F between \uD83D\uDC9A the text.")
                        }

                        item {
                            SpacerVertical(GridUnit.x2)
                        }

                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(
                                    modifier = Modifier.padding(
                                        vertical = GridUnit.x2,
                                        horizontal = GridUnit.x2,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    text = "ThreemaText - Emojis Android",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        item {
                            SpacerVertical(GridUnit.x1)
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "This text does not contain any emojies.", useAndroidEmojis = true)
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "One emoji at the end: \uD83C\uDF36", useAndroidEmojis = true)
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36", useAndroidEmojis = true)
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A", useAndroidEmojis = true)
                        }

                        item {
                            ThreemaTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A\uD83D\uDC1F", useAndroidEmojis = true)
                        }

                        item {
                            ThreemaTextEmojiShowcase(
                                rawInput = "Just \uD83C\uDF36 in \uD83D\uDC1F between \uD83D\uDC9A the text.",
                                useAndroidEmojis = true,
                            )
                        }

                        item {
                            SpacerVertical(GridUnit.x2)
                        }

                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(
                                    modifier = Modifier.padding(
                                        vertical = GridUnit.x2,
                                        horizontal = GridUnit.x2,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    text = "ThreemaText - Mentions (clickable)",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        item {
                            SpacerVertical(GridUnit.x1)
                        }

                        item {
                            ThreemaTextMentionShowcase(rawInput = "No mentions at all in this text")
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "Hey @[0123ABCD], how are you?")
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "Hey @[@@@@@@@@], how are you all?")
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "Hey @[0123ABCD] and @[3210DCBA], how are the two of you?")
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "@[0123ABCD] Mention at start")
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "Mention at end @[0123ABCD]")
                        }
                        item {
                            ThreemaTextMentionShowcase(
                                rawInput = "Chained mentions: @[0123ABCD] @[3210DCBA] @[@@@@@@@@] @[0123ABCD] @[3210DCBA] @[@@@@@@@@] " +
                                    "@[0123ABCD] @[3210DCBA] @[@@@@@@@@] @[0123ABCD] ",
                            )
                        }
                        item {
                            ThreemaTextMentionShowcase(rawInput = "Mention with an asterisks identity: @[*0123456]")
                        }

                        item {
                            SpacerVertical(GridUnit.x2)
                        }

                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(
                                            vertical = GridUnit.x2,
                                            horizontal = GridUnit.x2,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    text = "ThreemaText - All Features",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        item {
                            SpacerVertical(GridUnit.x1)
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .padding(
                                        vertical = GridUnit.x1,
                                        horizontal = GridUnit.x2,
                                    ),
                            ) {
                                ConversationText(
                                    modifier = Modifier.padding(horizontal = GridUnit.x0_5),
                                    rawInput = "Just \uD83C\uDF36 emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing text also mentioning " +
                                        "@[0123ABCD] @[3210DCBA] \uD83C\uDFD4 @[@@@@@@@@] and not to forget @[*0123456] \uD83C\uDFD4 ",
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    emojiSettings = ConversationTextDefaults.EmojiSettings,
                                    mentionFeature = MentionFeature.On(
                                        ownIdentity = "01234567",
                                        identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                                    ),
                                    onClickedMention = { identity ->
                                        Toaster.showToast(
                                            message = "Clicked on mention for identity $identity",
                                        )
                                    },
                                )
                            }
                        }

                        item {
                            SpacerVertical(GridUnit.x2)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TopLevelSectionHeader(
        modifier: Modifier = Modifier,
        name: String,
    ) {
        Text(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = GridUnit.x3, horizontal = GridUnit.x2),
            text = name,
            style = MaterialTheme.typography.headlineSmall,
        )
    }

    private data class ColorSection(
        val name: String,
        val colors: List<Pair<Color, String>>,
    )
}

private fun Color.toHexCode(): String {
    val red = this.red * 255
    val green = this.green * 255
    val blue = this.blue * 255
    return String.format("#%02x%02x%02x", red.toInt(), green.toInt(), blue.toInt())
}

@Composable
private fun ColorSpot(
    modifier: Modifier = Modifier,
    color: Color,
    colorName: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tearShape = RoundedCornerShape(
            topStartPercent = 5,
            topEndPercent = 50,
            bottomEndPercent = 50,
            bottomStartPercent = 50,
        )
        val contentColor = MaterialTheme.colorScheme.onSurface

        Box(
            modifier = Modifier
                .padding(all = GridUnit.x1)
                .size(size = 100.dp)
                .shadow(
                    elevation = GridUnit.x0_5,
                    shape = tearShape,
                )
                .clip(tearShape)
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = contentColor,
                    ),
                    shape = tearShape,
                )
                .background(color),
        ) { }

        Spacer(modifier = Modifier.width(GridUnit.x3))

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = colorName,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            SelectionContainer {
                Text(
                    text = color.toHexCode().toUpperCase(Locale.current),
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun ThreemaTextEmojiShowcase(
    rawInput: String,
    useAndroidEmojis: Boolean = false,
) {
    Box(
        modifier = Modifier
            .padding(
                vertical = GridUnit.x1,
                horizontal = GridUnit.x2,
            )
            .clip(
                RoundedCornerShape(GridUnit.x0_5),
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        ConversationText(
            modifier = Modifier.padding(horizontal = GridUnit.x0_5),
            rawInput = rawInput,
            textStyle = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                style = when (useAndroidEmojis) {
                    true -> EmojiStyle_ANDROID
                    false -> EmojiStyle_DEFAULT
                },
            ),
            mentionFeature = MentionFeature.On(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
            ),
        )
    }
}

@Composable
private fun ThreemaTextMentionShowcase(rawInput: String) {
    Box(
        modifier = Modifier
            .padding(
                vertical = GridUnit.x1,
                horizontal = GridUnit.x2,
            ),
    ) {
        ConversationText(
            modifier = Modifier.padding(horizontal = GridUnit.x0_5),
            rawInput = rawInput,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            onClickedMention = { identity ->
                Toaster.showToast(
                    message = "Clicked on mention for identity $identity",
                )
            },
            mentionFeature = MentionFeature.On(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
            ),
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_NO,
    group = "ColorSpot",
)
@Composable
private fun ColorSpot_Preview() {
    ThreemaThemePreview {
        ColorSpot(
            color = MaterialTheme.colorScheme.primary,
            colorName = "primary",
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_NO,
    group = "ColorSpot",
)
@Composable
private fun ColorSpot_Preview_Surface() {
    ThreemaThemePreview {
        ColorSpot(
            color = MaterialTheme.colorScheme.surface,
            colorName = "surface",
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    group = "ColorSpot",
)
@Composable
private fun ColorSpot_Preview_Night() {
    ThreemaThemePreview {
        ColorSpot(
            color = MaterialTheme.colorScheme.primary,
            colorName = "primary",
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    group = "ColorSpot",
)
@Composable
private fun ColorSpot_Preview_Night_Surface() {
    ThreemaThemePreview {
        ColorSpot(
            color = MaterialTheme.colorScheme.surface,
            colorName = "surface",
        )
    }
}
