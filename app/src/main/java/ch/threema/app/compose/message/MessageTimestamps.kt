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

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.app.messagedetails.MessageTimestampsUiModel
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.capitalize
import ch.threema.common.now
import ch.threema.common.plus
import java.util.Date
import kotlin.time.Duration.Companion.minutes

@Composable
fun MessageTimestampsListBox(
    modifier: Modifier = Modifier,
    messageTimestampsUiModel: MessageTimestampsUiModel,
    isOutbox: Boolean,
) {
    val borderColor: Color = if (isOutbox) {
        CustomColors.chatBubbleSendContainer
    } else {
        CustomColors.chatBubbleReceiveContainer
    }
    MessageTimestampsList(
        modifier = modifier
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
            .then(
                stringResource(R.string.cd_message_details_container).let { contentDescription ->
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        isTraversalGroup = true
                    }
                },
            ),
        model = messageTimestampsUiModel,
        displayIcons = true,
    )
}

@Composable
fun MessageTimestampsList(
    modifier: Modifier = Modifier,
    model: MessageTimestampsUiModel,
    displayIcons: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        model.createdAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_created),
                timestamp = model.createdAt,
                iconResId = if (displayIcons) R.drawable.ic_pencil_outline else null,
            )
        }
        model.sentAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_posted),
                timestamp = model.sentAt,
                iconResId = if (displayIcons) R.drawable.ic_mail_filled else null,
            )
        }
        model.receivedAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_received),
                timestamp = model.receivedAt,
                iconResId = if (displayIcons) R.drawable.ic_inbox_filled else null,
            )
        }
        model.deliveredAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_delivered).capitalize(),
                timestamp = model.deliveredAt,
                iconResId = if (displayIcons) R.drawable.ic_inbox_filled else null,
            )
        }
        model.readAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_read).capitalize(),
                timestamp = model.readAt,
                iconResId = if (displayIcons) R.drawable.ic_mark_read else null,
            )
        }
        model.modifiedAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_modified),
                timestamp = model.modifiedAt,
                iconResId = if (displayIcons) R.drawable.ic_edit_file_name else null,
            )
        }
        model.editedAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_edited),
                timestamp = model.editedAt,
                iconResId = if (displayIcons) R.drawable.ic_edit_file_name else null,
            )
        }
        model.deletedAt?.let {
            MessageTimestampsRow(
                label = stringResource(R.string.state_dialog_deleted),
                timestamp = model.deletedAt,
                iconResId = if (displayIcons) R.drawable.ic_delete_outline else null,
            )
        }
    }
}

@Composable
private fun MessageTimestampsRow(
    label: String,
    timestamp: Date,
    @DrawableRes iconResId: Int?,
) {
    MessageDetailsRow(
        label = label,
        value = LocaleUtil.formatTimeStampStringAbsolute(LocalContext.current, timestamp.time),
        iconResId = iconResId,
    )
}

@Preview(name = "Outbox", group = "box")
@Composable
private fun MessageTimestampsListBoxPreview_Outbox() {
    MessageTimestampsListBox(
        modifier = Modifier.padding(16.dp),
        messageTimestampsUiModel = MessageTimestampsUiModel(
            createdAt = now(),
            sentAt = now(),
            receivedAt = now() + 1.minutes,
            deliveredAt = now() + 1.minutes,
            readAt = now() + 10.minutes,
            modifiedAt = now() + 10.minutes,
            editedAt = now() + 5.minutes,
            deletedAt = now() + 15.minutes,
        ),
        isOutbox = true,
    )
}

@Preview(name = "Inbox", group = "box")
@Composable
private fun MessageTimestampsListBoxPreview_Inbox() {
    MessageTimestampsListBox(
        modifier = Modifier.padding(16.dp),
        messageTimestampsUiModel = MessageTimestampsUiModel(
            createdAt = now(),
            sentAt = now(),
            deliveredAt = now() + 1.minutes,
            readAt = now() + 10.minutes,
            modifiedAt = now() + 10.minutes,
            editedAt = now() + 5.minutes,
            deletedAt = now() + 15.minutes,
        ),
        isOutbox = false,
    )
}

@Preview
@Composable
private fun MessageTimestampsList_Preview() {
    MessageTimestampsList(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        model = MessageTimestampsUiModel(
            createdAt = now(),
            sentAt = now(),
            deliveredAt = now() + 1.minutes,
            readAt = now() + 10.minutes,
            modifiedAt = now() + 10.minutes,
            editedAt = now() + 5.minutes,
            deletedAt = now() + 15.minutes,
        ),
    )
}
