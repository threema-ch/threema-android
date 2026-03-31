package ch.threema.app.dev.patternlibrary

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ch.threema.app.compose.preview.PreviewData

/**
 *  Only used for the pattern library.
 *  Do not use elsewhere.
 */
@Immutable
object PatternLibraryData {

    val colorSections: List<ColorSection>
        @Composable
        get() = listOf(
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

    val conversationTextInputsMarkup = listOf(
        // Basic
        "*bold*",
        "_italic_",
        "~strikethrough~",
        "word word *bold* word _italic_ word ~strikethrough~",

        // Combinations
        "word *_bold-italic_* word",
        "word _~italic-strikethrough~_ word",
        "word *_~bold-italic-strikethrough~_* word",

        // Nested bold
        "word *bold _bold-italic_* word",
        "word *bold _bold-italic_ bold* word",
        "word *bold ~bold-strikethrough~* word",
        "word *bold ~bold-strikethrough~ bold* word",

        // Nested deep
        "word *bold _bold-italic ~bold-italic-strikethrough~ bold-italic_ bold* word",
        "word _italic *italic-bold ~italic-bold-strikethrough~ italic-bold* italic_ word",
        "word ~strikethrough *strikethrough-bold _strikethrough-bold-italic_ strikethrough-bold* strikethrough~ word",

        // Unclosed
        "word *word",
        "word *bold _bold*",

        // URL
        "word https://link.ch/a/b*c*d word",
        "word *bold https://link.ch/a/b_c_d bold* word",

        // Newline
        "word *_italic_ \n word* word _italic_",
    )

    val conversationTextHighlights = mapOf(
        "This is a highlighted part" to "highlighted",
        "This is a *bold-highlighted* part" to "bold-highlighted",
        "This is a *_bold-italic-highlighted_* part" to "bold-italic-highlighted",
        "This is a _*~italic-bold-strikethrough-highlighted~*_ part" to "italic-bold-strikethrough-highlighted",
        "This is a case insensitive Highlighted part" to "highlighted",
        "A mention to @[${PreviewData.IDENTITY_OTHER_1}] followed by a highlight" to "highlight",
    )
}

@Immutable
data class ColorSection(
    val name: String,
    val colors: List<Pair<Color, String>>,
)
