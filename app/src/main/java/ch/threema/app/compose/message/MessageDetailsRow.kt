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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.AppTypography

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MessageDetailsRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    @DrawableRes iconResId: Int? = null,
    selectableValueOption: SelectableValueOption = SelectableValueOption.NotSelectable
) {

    val context = LocalContext.current

    fun copyToClipboardAndShowHintLink() {
        copyToClipboardAndShowHint(
            context = context,
            value = value,
            selectableValueOption = selectableValueOption as SelectableValueOption.Selectable
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (iconResId != null) {
            Icon(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .padding(end = 12.dp)
                    .size(20.dp),
                painter = painterResource(id = iconResId),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null
            )
        }

        FlowRow(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThemedText(
                modifier = Modifier.padding(end = 16.dp),
                text = label,
                style = AppTypography.labelLarge,
            )

            Surface(
                modifier = Modifier.combinedClickable(
                    enabled = selectableValueOption.isSelectable,
                    onLongClick = ::copyToClipboardAndShowHintLink,
                    onDoubleClick = ::copyToClipboardAndShowHintLink,
                    onClick = {}
                ),
                color = Color.Transparent
            ) {
                ThemedText(
                    text = value,
                    style = AppTypography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

sealed class SelectableValueOption(val isSelectable: Boolean) {
    data object NotSelectable : SelectableValueOption(isSelectable = false)

    data class Selectable(
        @StringRes val onValueCopiedNoticeStringResId: Int
    ) : SelectableValueOption(isSelectable = true)
}

private fun copyToClipboardAndShowHint(
    context: Context,
    value: String,
    selectableValueOption: SelectableValueOption.Selectable,
) {
    val stringResId = selectableValueOption.onValueCopiedNoticeStringResId
    val clip = ClipData.newPlainText(null, value)
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    Toast.makeText(context, context.getString(stringResId), Toast.LENGTH_LONG).show()
}

@Preview
@Composable
private fun MessageDetailsRowPreview() {
    MessageDetailsRow(
        modifier = Modifier.padding(16.dp),
        label = "Label",
        value = "Value"
    )
}

@Preview
@Composable
private fun MessageDetailsRowPreview_Icon() {
    MessageDetailsRow(
        modifier = Modifier.padding(16.dp),
        label = "Label",
        value = "Value",
        iconResId = R.drawable.ic_key_outline
    )
}

@Preview
@Composable
private fun MessageDetailsRowPreview_Multiline() {
    MessageDetailsRow(
        modifier = Modifier.padding(16.dp),
        label = "Labelllllllllllllllllllllllllllllllllllllllllll",
        value = "Valueeeeeeeeeeeeeeeeeeeeeeeeeeee"
    )
}

@Preview
@Composable
private fun MessageDetailsRowPreview_Multiline_Icon() {
    MessageDetailsRow(
        modifier = Modifier.padding(16.dp),
        label = "Labelllllllllllllllllllllllllllllllllllllllllll",
        value = "Valueeeeeeeeeeeeeeeeeeeeeeeeeeee",
        iconResId = R.drawable.ic_key_outline
    )
}
