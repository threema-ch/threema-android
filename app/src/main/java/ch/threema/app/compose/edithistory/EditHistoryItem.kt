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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.anim.AnimatedVisibilityNow
import ch.threema.app.compose.message.MessageBubble
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.ui.CustomTextSelectionCallback
import ch.threema.app.utils.LocaleUtil
import ch.threema.data.models.EditHistoryEntryData
import java.util.Date

@Composable
fun EditHistoryTimelineItem(
    modifier: Modifier = Modifier,
    bubbleModifier: Modifier = Modifier,
    editHistoryEntry: EditHistoryEntryData,
    isOutbox: Boolean,
    shouldMarkupText: Boolean,
    isExpanded: Boolean = false,
    shouldFadeOutTimeLineTop: Boolean = false,
    shouldFadeOutTimeLineBottom: Boolean = false,
    textSelectionCallback: CustomTextSelectionCallback? = null,
    onClick: (() -> Unit)? = null,
) {
    VerticalTimelineItem(
        modifier = modifier
            .semantics(mergeDescendants = true) { },
        color = if (isOutbox) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shouldFadeOutLineTop = shouldFadeOutTimeLineTop,
        shouldFadeOutLineBottom = shouldFadeOutTimeLineBottom,
        labelContent = {
            EditedAtLabel(editHistoryEntry.editedAt)
        },
    ) {
        EditHistoryItemBubble(
            bubbleModifier = bubbleModifier,
            editHistoryEntry = editHistoryEntry,
            isOutbox = isOutbox,
            shouldMarkupText = shouldMarkupText,
            isExpanded = isExpanded,
            textSelectionCallback = textSelectionCallback,
            onClick = onClick,
        )
    }
}

@Composable
fun EditHistoryItem(
    modifier: Modifier = Modifier,
    bubbleModifier: Modifier = Modifier,
    editHistoryEntry: EditHistoryEntryData,
    isOutbox: Boolean,
    shouldMarkupText: Boolean,
    isExpanded: Boolean = false,
    textSelectionCallback: CustomTextSelectionCallback? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        EditedAtLabel(editHistoryEntry.editedAt)
        Spacer(Modifier.height(4.dp))
        EditHistoryItemBubble(
            bubbleModifier = bubbleModifier,
            editHistoryEntry = editHistoryEntry,
            isOutbox = isOutbox,
            shouldMarkupText = shouldMarkupText,
            isExpanded = isExpanded,
            textSelectionCallback = textSelectionCallback,
            onClick = onClick,
        )
    }
}

@Composable
private fun EditHistoryItemBubble(
    modifier: Modifier = Modifier,
    bubbleModifier: Modifier = Modifier,
    editHistoryEntry: EditHistoryEntryData,
    isOutbox: Boolean,
    shouldMarkupText: Boolean,
    isExpanded: Boolean = false,
    textSelectionCallback: CustomTextSelectionCallback? = null,
    onClick: (() -> Unit)? = null,
) {
    /**
     * If we encounter a blank/null value in [EditHistoryEntryData.text] we can
     * be sure it is an EditHistoryEntryData of a file message. Because you cant
     * clear the text of a normal text message through editing it. So in this case
     * we want to style the message bubble body text slightly different and set a
     * placeholder text like `No caption`
     */
    val isEmptyFileMessageCaption: Boolean = editHistoryEntry.text.isNullOrBlank()

    Box(modifier) {
        AnimatedVisibilityNow {
            MessageBubble(
                modifier = bubbleModifier
                    .padding(bottom = 16.dp),
                text = editHistoryEntry.text
                    ?: stringResource(R.string.edit_history_file_no_caption),
                isOutbox = isOutbox,
                shouldMarkupText = shouldMarkupText,
                onClick = onClick,
                textSelectionCallback = textSelectionCallback,
                textAppearanceRes = if (isEmptyFileMessageCaption) {
                    R.style.Threema_Bubble_Text_Body_HistoryNoCaption
                } else {
                    R.style.Threema_Bubble_Text_Body
                },
            )
        }

        // overlay to fade out bottom when collapsed
        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter),
            visible = !isExpanded,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                .7f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun EditedAtLabel(editedAt: Date) {
    val formattedEditedAtDate = LocaleUtil.formatTimeStampStringAbsolute(
        LocalContext.current,
        editedAt.time,
    )
    AnimatedVisibilityNow {
        ThemedText(
            modifier = Modifier
                .padding(start = 4.dp)
                .then(
                    stringResource(R.string.cd_edited_at).let {
                        Modifier.semantics {
                            contentDescription = it.format(formattedEditedAtDate)
                        }
                    },
                ),
            text = formattedEditedAtDate,
            style = AppTypography.labelLarge,
        )
    }
}
