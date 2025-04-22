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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.customColorScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class PatternLibraryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ThreemaTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.shadow(elevation = 8.dp),
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
                ) { padding ->

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
                        ColorSection(
                            "Custom",
                            listOf(
                                MaterialTheme.customColorScheme.messageBubbleContainerReceive to "messageBubbleContainerReceive",
                            ),
                        ),
                    )

                    LazyColumn(
                        modifier = Modifier.padding(padding),
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
                                            horizontal = 16.dp,
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
                                        vertical = 4.dp,
                                        horizontal = 12.dp,
                                    ),
                                    color = colorSection.colors[index].first,
                                    colorName = colorSection.colors[index].second,
                                )
                            }
                        }

                        item {
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                TopLevelSectionHeader(name = "Typography")
                            }
                        }

                        item {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                text = "Not yet implemented",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                            )
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
                .padding(vertical = 24.dp, horizontal = 16.dp),
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
                .padding(all = 8.dp)
                .size(size = 100.dp)
                .shadow(
                    elevation = 4.dp,
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

        Spacer(modifier = Modifier.width(24.dp))

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
