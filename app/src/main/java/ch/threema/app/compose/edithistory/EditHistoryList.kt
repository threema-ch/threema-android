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

package ch.threema.app.compose.edithistory

import android.annotation.SuppressLint
import android.content.Context.ACCESSIBILITY_SERVICE
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.anim.ExpandingBox
import ch.threema.app.ui.CustomTextSelectionCallback

private val ITEM_MAX_HEIGHT = 196.dp

@Composable
@SuppressLint("ComposableLambdaParameterNaming")
fun EditHistoryList(
    modifier: Modifier,
    contentPadding: PaddingValues,
    editHistoryUiState: EditHistoryUiState,
    isOutbox: Boolean,
    shouldMarkupText: Boolean,
    textSelectionCallback: CustomTextSelectionCallback? = null,
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // check if talkback is enabled
    val isExploreByTouchEnabled = produceState(false) {
        val am: AccessibilityManager? =
            context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager?
        value = am?.isTouchExplorationEnabled ?: false
    }

    val isItemExpandableMap = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(editHistoryUiState.editHistoryEntries) {
        isItemExpandableMap.keys.retainAll(
            editHistoryUiState.editHistoryEntries.map { it.uid }
                .toSet(),
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item {
            headerContent?.invoke()
        }
        if (editHistoryUiState.editHistoryEntries.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // edit history label
            item {
                ThemedText(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics { isTraversalGroup = true },
                    text = stringResource(R.string.edit_history),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // edit history
            items(
                count = editHistoryUiState.editHistoryEntries.size,
                key = { index -> editHistoryUiState.editHistoryEntries[index].uid },
            ) { index ->
                val entry = editHistoryUiState.editHistoryEntries[index]
                val isExpandable by remember {
                    derivedStateOf {
                        isItemExpandableMap[entry.uid] ?: false
                    }
                }

                ExpandingBox(
                    collapsedMaxHeight = if (isExpandable) ITEM_MAX_HEIGHT else null,
                ) { isExpanded, toggleExpanded ->
                    val itemModifier = Modifier
                        .padding(start = 8.dp)
                        .then(
                            stringResource(R.string.cd_index_in_edit_history).let {
                                Modifier.semantics {
                                    contentDescription =
                                        it.format(editHistoryUiState.editHistoryEntries.size - index)
                                }
                            },
                        )
                    val bubbleModifier = Modifier.onGloballyPositioned { coordinates ->
                        // measure bubble height to initialize whether it is expandable
                        if (!isItemExpandableMap.containsKey(entry.uid)) {
                            val heightInDp = (coordinates.size.height / density.density).dp
                            isItemExpandableMap[entry.uid] =
                                !isExploreByTouchEnabled.value && heightInDp > ITEM_MAX_HEIGHT
                        }
                    }
                    if (editHistoryUiState.editHistoryEntries.size > 1) {
                        EditHistoryTimelineItem(
                            modifier = itemModifier,
                            bubbleModifier = bubbleModifier,
                            editHistoryEntry = entry,
                            isOutbox = isOutbox,
                            shouldMarkupText = shouldMarkupText,
                            isExpanded = if (isExpandable) isExpanded else true,
                            onClick = if (isExpandable) toggleExpanded else null,
                            shouldFadeOutTimeLineTop = index == 0,
                            shouldFadeOutTimeLineBottom = index == editHistoryUiState.editHistoryEntries.size - 1,
                            textSelectionCallback = textSelectionCallback,
                        )
                    } else {
                        EditHistoryItem(
                            modifier = itemModifier.padding(start = 8.dp),
                            bubbleModifier = bubbleModifier,
                            editHistoryEntry = entry,
                            isOutbox = isOutbox,
                            shouldMarkupText = shouldMarkupText,
                            isExpanded = if (isExpandable) isExpanded else true,
                            onClick = if (isExpandable) toggleExpanded else null,
                            textSelectionCallback = textSelectionCallback,
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            footerContent?.invoke()
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
