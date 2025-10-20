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

package ch.threema.app.compose.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun MessageDebugInfoBox(
    modifier: Modifier = Modifier,
    rowId: Int,
    uid: String,
    isOutbox: Boolean,
) {
    val borderColor: Color = if (isOutbox) {
        CustomColors.chatBubbleSendContainer
    } else {
        CustomColors.chatBubbleReceiveContainer
    }
    MessageDebugInfoList(
        modifier = modifier
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(GridUnit.x2)
            .then(
                stringResource(R.string.cd_message_details_container).let { contentDescription ->
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        isTraversalGroup = true
                    }
                },
            ),
        rowId = rowId,
        uid = uid,
    )
}

@Composable
private fun MessageDebugInfoList(
    modifier: Modifier = Modifier,
    rowId: Int,
    uid: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridUnit.x0_5),
    ) {
        MessageDetailsRow(
            label = "rowId",
            value = rowId.toString(),
            selectableValueOption = SelectableValueOption.Selectable(
                onValueCopiedNoticeStringResId = R.string.generic_copied_to_clipboard_hint,
            ),
        )
        MessageDetailsRow(
            label = "uid",
            value = uid,
            selectableValueOption = SelectableValueOption.Selectable(
                onValueCopiedNoticeStringResId = R.string.generic_copied_to_clipboard_hint,
            ),
        )
    }
}

@Preview(name = "Outbox", group = "box")
@Composable
private fun MessageDetailsListBoxPreview_Outbox() {
    ThreemaThemePreview {
        MessageDebugInfoBox(
            modifier = Modifier.padding(GridUnit.x2),
            rowId = 123,
            uid = "haha-not-very-unique",
            isOutbox = true,
        )
    }
}

@Preview(name = "Inbox", group = "box")
@Composable
private fun MessageDetailsListBoxPreview_Inbox() {
    ThreemaThemePreview {
        MessageDebugInfoBox(
            modifier = Modifier.padding(GridUnit.x2),
            rowId = 123,
            uid = "haha-not-very-unique",
            isOutbox = false,
        )
    }
}

@Preview
@Composable
private fun MessageDetailsListPreview() {
    ThreemaThemePreview {
        MessageDebugInfoList(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(GridUnit.x2),
            rowId = 123,
            uid = "haha-not-very-unique",
        )
    }
}
