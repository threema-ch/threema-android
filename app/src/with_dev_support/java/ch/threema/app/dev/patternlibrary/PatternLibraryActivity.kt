package ch.threema.app.dev.patternlibrary

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.android.buildActivityIntent
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.TextButtonPrimary
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryDense
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryWebsite
import ch.threema.app.compose.common.buttons.primary.ExtendedFloatingActionButtonPrimary
import ch.threema.app.compose.common.buttons.primary.FloatingActionButtonPrimary
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.ConversationTextDefaults
import ch.threema.app.compose.common.text.conversation.EmojiSettings
import ch.threema.app.compose.common.text.conversation.EmojiUpscalingFeature
import ch.threema.app.compose.common.text.conversation.HighlightFeature
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService.Companion.EMOJI_STYLE_ANDROID
import ch.threema.app.preference.service.PreferenceService.Companion.EMOJI_STYLE_DEFAULT
import ch.threema.app.services.ContactService
import ch.threema.domain.types.Identity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PatternLibraryActivity : AppCompatActivity(), KoinComponent {

    private val sharedPreferences: SharedPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val shouldUseDynamicColorsUserSetting: Boolean = remember {
                sharedPreferences.getBoolean("pref_dynamic_color", false)
            }
            var shouldUseDynamicColorsOverride: Boolean by rememberSaveable(shouldUseDynamicColorsUserSetting) {
                mutableStateOf(shouldUseDynamicColorsUserSetting)
            }

            ThreemaThemePatternLibrary(
                shouldUseDynamicColorsOverride = shouldUseDynamicColorsOverride,
            ) {
                PatternLibraryContent(
                    onClickBack = {
                        finish()
                    },
                    onClickToggleDynamicColors = {
                        shouldUseDynamicColorsOverride = !shouldUseDynamicColorsOverride
                        showToast("Dynamic colors ${if (shouldUseDynamicColorsOverride) "enabled" else "disabled"}")
                    },
                    onClickAnyButtonComponent = {
                        showToast("Clicked a button")
                    },
                    onClickMention = { identity ->
                        showToast("Clicked on mention for identity $identity")
                    },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<PatternLibraryActivity>(context)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PatternLibraryContent(
    onClickBack: () -> Unit,
    onClickToggleDynamicColors: () -> Unit,
    onClickAnyButtonComponent: () -> Unit,
    onClickMention: (Identity) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(elevation = GridUnit.x1),
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
                scrollBehavior = scrollBehavior,
                title = {
                    ThemedText(
                        text = "Pattern Library",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClickBack,
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_arrow_back_24),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClickToggleDynamicColors,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_magic_wand_white_24dp),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { scaffoldContentPadding ->

        val colorSections = PatternLibraryData.colorSections

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = scaffoldContentPadding,
        ) {
            buttons(onClickAnyButtonComponent)

            colors(colorSections)

            typography()

            componentConversationText(onClickMention)

            item {
                SpacerVertical(GridUnit.x2)
            }
        }
    }
}

@Composable
private fun TopLevelSectionHeader(
    modifier: Modifier = Modifier,
    name: String,
) {
    ThemedText(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = GridUnit.x3, horizontal = GridUnit.x2),
        text = name,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SubLevelSectionHeader(
    modifier: Modifier = Modifier,
    name: String,
) {
    ThemedText(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                vertical = 12.dp,
                horizontal = GridUnit.x2,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        text = name,
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun LazyListScope.buttons(
    onClickedAnyButtonComponent: () -> Unit,
) {
    item {
        TopLevelSectionHeader(name = "Buttons")
    }

    item {
        SubLevelSectionHeader(name = "Primary Button")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Button",
        )
    }

    item {
        ButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Button",
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            maxLines = 2,
        )
    }

    item {
        ButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            enabled = false,
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        SubLevelSectionHeader(name = "Primary Button Dense")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ButtonPrimaryDense(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x0_5),
            onClick = onClickedAnyButtonComponent,
            text = "Dense",
        )
    }

    item {
        ButtonPrimaryDense(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x0_5),
            onClick = onClickedAnyButtonComponent,
            text = "Dense",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimaryDense(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x0_5),
            onClick = onClickedAnyButtonComponent,
            text = "Dense",
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimaryDense(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x0_5),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            maxLines = 2,
        )
    }

    item {
        ButtonPrimaryDense(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x0_5),
            onClick = onClickedAnyButtonComponent,
            text = "Dense",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            enabled = false,
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        SubLevelSectionHeader(name = "Primary Button Website")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Button Website",
        )
    }

    item {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Button Website",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Button Website",
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            maxLines = 2,
        )
    }

    item {
        ButtonPrimaryWebsite(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Button Website",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            enabled = false,
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        SubLevelSectionHeader(name = "Button Outlined")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ButtonOutlined(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Outlined Button",
        )
    }

    item {
        ButtonOutlined(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Outlined Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ButtonOutlined(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            maxLines = 2,
        )
    }

    item {
        ButtonOutlined(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Outlined Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            enabled = false,
        )
    }

    item {
        SubLevelSectionHeader(name = "Text Button")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        TextButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Text Button",
        )
    }

    item {
        TextButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Text Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        TextButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Text Button",
            trailingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        TextButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            maxLines = 2,
        )
    }

    item {
        TextButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Primary Text Button",
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
            enabled = false,
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        SubLevelSectionHeader(name = "Primary FAB")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        FloatingActionButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            icon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        SubLevelSectionHeader(name = "Primary Extended FAB")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ExtendedFloatingActionButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Expanded",
            icon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        ExtendedFloatingActionButtonPrimary(
            modifier = Modifier.padding(horizontal = GridUnit.x2, vertical = GridUnit.x1),
            onClick = onClickedAnyButtonComponent,
            text = "Lorem ipsum dolor sit amet consectetur adipiscing elit. Dolor sit amet consectetur adipiscing elit quisque faucibus.",
            icon = ButtonIconInfo(
                icon = R.drawable.ic_color_lens_24px,
                contentDescription = null,
            ),
        )
    }

    item {
        SpacerVertical(GridUnit.x1)
    }
}

private fun LazyListScope.colors(colorSections: List<ColorSection>) {
    item {
        TopLevelSectionHeader(name = "Colors")
    }

    colorSections.forEach { colorSection ->

        item {
            SubLevelSectionHeader(name = colorSection.name)
        }
        items(colorSection.colors) { (color, colorName) ->
            ColorSpot(
                modifier = Modifier.padding(
                    vertical = GridUnit.x0_5,
                    horizontal = 12.dp,
                ),
                color = color,
                colorName = colorName,
            )
        }
    }
}

private fun LazyListScope.typography() {
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
}

private fun LazyListScope.componentConversationText(
    onClickMention: (Identity) -> Unit,
) {
    item {
        TopLevelSectionHeader(name = "ConversationText")
    }

    item {
        SubLevelSectionHeader(name = "Markup")
    }

    items(PatternLibraryData.conversationTextInputsMarkup) { rawInput ->
        ConversationText(
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
                )
                .padding(horizontal = GridUnit.x0_5),
            rawInput = rawInput,
            mentionFeature = MentionFeature.Off,
            markupEnabled = true,
        )
    }

    item {
        SubLevelSectionHeader(name = "Emojis Default")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "This text does not contain any emojies.")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "One emoji at the end: \uD83C\uDF36")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A\uD83D\uDC1F")
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "Just \uD83C\uDF36 in \uD83D\uDC1F between \uD83D\uDC9A the text.")
    }

    item {
        SpacerVertical(GridUnit.x2)
    }

    item {
        SubLevelSectionHeader(name = "Emojis Android")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "This text does not contain any emojies.", useAndroidEmojis = true)
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "One emoji at the end: \uD83C\uDF36", useAndroidEmojis = true)
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36", useAndroidEmojis = true)
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A", useAndroidEmojis = true)
    }

    item {
        ConversationTextEmojiShowcase(rawInput = "\uD83C\uDF36\uD83C\uDFD4\uD83D\uDC9A\uD83D\uDC1F", useAndroidEmojis = true)
    }

    item {
        ConversationTextEmojiShowcase(
            rawInput = "Just \uD83C\uDF36 in \uD83D\uDC1F between \uD83D\uDC9A the text.",
            useAndroidEmojis = true,
        )
    }

    item {
        SpacerVertical(GridUnit.x2)
    }

    item {
        SubLevelSectionHeader(name = "Mentions (clickable)")
    }

    item {
        SpacerVertical(GridUnit.x1)
    }

    item {
        ThreemaTextMentionShowcase(rawInput = "No mentions at all in this text")
    }
    item {
        ThreemaTextMentionShowcase(rawInput = "Hey @[${PreviewData.IDENTITY_OTHER_1}], how are you?")
    }
    item {
        ThreemaTextMentionShowcase(rawInput = "Hey @[${ContactService.ALL_USERS_PLACEHOLDER_ID}], how are you all?")
    }
    item {
        ThreemaTextMentionShowcase(
            rawInput = "Hey @[${PreviewData.IDENTITY_OTHER_1}] and @[${PreviewData.IDENTITY_OTHER_2}], how are the two of you?",
        )
    }
    item {
        ThreemaTextMentionShowcase(rawInput = "@[${PreviewData.IDENTITY_OTHER_1}] Mention at start")
    }
    item {
        ThreemaTextMentionShowcase(rawInput = "Mention at end @[${PreviewData.IDENTITY_OTHER_1}]")
    }
    item {
        ThreemaTextMentionShowcase(
            rawInput = "Chained mentions: @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] " +
                "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] " +
                "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] @[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] " +
                "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] @[${PreviewData.IDENTITY_OTHER_1}] ",
        )
    }
    item {
        ThreemaTextMentionShowcase(rawInput = "Mention with an asterisks identity: @[${PreviewData.IDENTITY_BROADCAST}]")
    }

    item {
        SpacerVertical(GridUnit.x2)
    }

    item {
        SubLevelSectionHeader(name = "Highlight")
    }

    PatternLibraryData.conversationTextHighlights.forEach { highlightPair ->
        item {
            val activity = LocalActivity.current
            ConversationText(
                modifier = Modifier
                    .padding(
                        vertical = GridUnit.x1,
                        horizontal = GridUnit.x2,
                    )
                    .padding(horizontal = GridUnit.x0_5),
                rawInput = highlightPair.key,
                mentionFeature = MentionFeature.On(
                    ownIdentity = PreviewData.IDENTITY_ME,
                    identityDisplayNames = PreviewData.mentionNames,
                    onClickMention = { identity ->
                        activity?.showToast(
                            message = "Clicked on mention for identity $identity",
                        )
                    },
                ),
                markupEnabled = true,
                emojiSettings = EmojiSettings(
                    style = EMOJI_STYLE_ANDROID,
                ),
                highlightFeature = HighlightFeature.On(
                    highlightedContent = highlightPair.value,
                    ignoreCase = true,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    foregroundColor = MaterialTheme.colorScheme.onPrimary,
                    spotlight = HighlightFeature.Spotlight.None,
                ),
            )
        }
    }

    item {
        SpacerVertical(GridUnit.x2)
    }

    item {
        SubLevelSectionHeader(name = "All Features")
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
                rawInput = "Just \uD83C\uDF36 ~cool~ emojis \uD83D\uDC1F between \uD83D\uDC9A ongoing " +
                    "*bold text* also mentioning in _italic " +
                    "@[${PreviewData.IDENTITY_OTHER_1}] @[${PreviewData.IDENTITY_OTHER_2}] _ \uD83C\uDFD4 " +
                    "@[${ContactService.ALL_USERS_PLACEHOLDER_ID}] and not to forget @[${PreviewData.IDENTITY_BROADCAST}] \uD83C\uDFD4. ",
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                emojiSettings = ConversationTextDefaults.EmojiSettings,
                mentionFeature = MentionFeature.On(
                    ownIdentity = PreviewData.IDENTITY_ME,
                    identityDisplayNames = PreviewData.mentionNames,
                    onClickMention = onClickMention,
                ),
            )
        }
    }
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
                .size(size = 60.dp)
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
private fun ConversationTextEmojiShowcase(
    rawInput: String,
    useAndroidEmojis: Boolean = false,
) {
    ConversationText(
        modifier = Modifier
            .padding(
                vertical = GridUnit.x1,
                horizontal = GridUnit.x2,
            )
            .clip(
                shape = RoundedCornerShape(GridUnit.x0_5),
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(horizontal = GridUnit.x0_5),
        rawInput = rawInput,
        textStyle = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
            style = when (useAndroidEmojis) {
                true -> EMOJI_STYLE_ANDROID
                false -> EMOJI_STYLE_DEFAULT
            },
            upscalingFeature = EmojiUpscalingFeature.On(),
        ),
        mentionFeature = MentionFeature.Off,
    )
}

@Composable
private fun ThreemaTextMentionShowcase(rawInput: String) {
    val activity = LocalActivity.current
    ConversationText(
        modifier = Modifier.padding(
            vertical = GridUnit.x1,
            horizontal = GridUnit.x2_5,
        ),
        rawInput = rawInput,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        mentionFeature = MentionFeature.On(
            ownIdentity = PreviewData.IDENTITY_ME,
            identityDisplayNames = PreviewData.mentionNames,
            onClickMention = { identity ->
                activity?.showToast(
                    message = "Clicked on mention for identity $identity",
                )
            },
        ),
    )
}

@Preview(heightDp = 2000)
@Composable
private fun Content_Preview() {
    ThreemaThemePreview {
        PatternLibraryContent(
            onClickBack = {},
            onClickToggleDynamicColors = {},
            onClickAnyButtonComponent = {},
            onClickMention = {},
        )
    }
}
